package com.autoinsta.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.autoinsta.AutoInstaApp
import com.autoinsta.data.db.entities.PostHistoryEntity
import com.autoinsta.data.db.relations.ScheduledPostWithMedia
import com.autoinsta.data.repository.PublishResult
import com.autoinsta.domain.QueuePlanner
import com.autoinsta.domain.ScheduleCalculator
import com.autoinsta.domain.model.MissedPostPolicy
import com.autoinsta.domain.model.PostStatus
import com.autoinsta.domain.model.TimingMode
import java.io.File

/**
 * Does the actual publishing when a post comes due.
 *
 * Uploads the media somewhere Instagram can fetch it, creates the container, waits for
 * video to finish transcoding where relevant, publishes, and records the outcome.
 *
 * A failure is either permanent (Instagram will never accept this) or transient (network,
 * quota, Instagram having a moment). Only transient failures are retried — retrying a
 * rejected image every fifteen minutes would burn the daily quota for nothing.
 *
 * It runs as a `CoroutineWorker` rather than inside the alarm receiver because a
 * broadcast receiver gets only a few seconds before the system kills it, and uploading
 * media will take far longer than that. WorkManager also survives the process dying
 * mid-job and can retry.
 */
class PostWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val postId = inputData.getLong(KEY_POST_ID, -1L)
        if (postId <= 0L) return Result.failure()

        val app = applicationContext as AutoInstaApp
        val repository = app.postRepository
        val notifier = app.notifier

        val post = repository.getById(postId) ?: return Result.success() // deleted meanwhile
        if (post.post.status != PostStatus.SCHEDULED) return Result.success() // already handled

        // Re-check the decision at execution time. The alarm may have fired late, or the
        // device may have been off for days and this is the boot catch-up.
        //
        // The two timing modes disagree about exactly one thing: what "too late" means.
        // A fixed post can be missed, because its time was the point. A queued post never
        // is — it simply takes the next slot.
        val proceed = when (post.post.timingMode) {
            TimingMode.FIXED -> shouldPublishFixed(post)
            TimingMode.QUEUED -> shouldPublishQueued(post)
        }
        if (!proceed) return Result.success()

        repository.updateStatus(postId, PostStatus.POSTING)

        // Publishing is the one moment a valid token actually matters, and this worker
        // may be the only thing that runs for weeks. Cheap, and a no-op unless due.
        app.accountRepository.refreshIfNeeded()

        // The Phase 2.5 media work is what makes this check meaningful: these are our own
        // files, so a missing one is a real problem rather than an expired permission.
        val missing = post.mediaItems.filterNot { File(it.localUri).canRead() }
        if (missing.isNotEmpty()) {
            val reason = "${missing.size} media file(s) are missing from storage."
            fail(postId, post.post.caption, reason)
            notifier.notifyFailed(postId, post.post.caption, reason)
            leaveQueueIfQueued(post)
            return Result.success()
        }

        return when (val result = app.publishRepository.publish(post)) {
            is PublishResult.Success -> {
                repository.updateStatus(postId, PostStatus.POSTED)
                app.historyRepository.record(
                    PostHistoryEntity(
                        postId = postId,
                        postType = post.post.postType,
                        caption = post.post.caption,
                        hashtags = post.post.hashtags,
                        scheduledAt = post.post.scheduledAt,
                        postedAt = System.currentTimeMillis(),
                        status = PostStatus.POSTED,
                        instagramMediaId = result.mediaId,
                        errorMessage = null,
                    )
                )
                notifier.notifyPosted(postId, post.post.caption, post.mediaItems.size)
                leaveQueueIfQueued(post)
                Result.success()
            }

            is PublishResult.TransientFailure -> {
                // Put it back to SCHEDULED so the queue still shows it as pending and a
                // retry is not blocked by the "already handled" guard at the top.
                repository.updateStatus(postId, PostStatus.SCHEDULED)
                if (runAttemptCount >= MAX_RETRIES) {
                    fail(postId, post.post.caption, result.reason)
                    notifier.notifyFailed(postId, post.post.caption, result.reason)
                    leaveQueueIfQueued(post)
                    Result.failure()
                } else {
                    // WorkManager backs off exponentially between attempts.
                    Result.retry()
                }
            }

            is PublishResult.PermanentFailure -> {
                fail(postId, post.post.caption, result.reason)
                notifier.notifyFailed(postId, post.post.caption, result.reason)
                leaveQueueIfQueued(post)
                Result.failure()
            }
        }
    }

    /**
     * A fixed-time post: the owner chose that moment, so being far too late is a real
     * failure and is recorded as one, per the post's own [MissedPostPolicy].
     */
    private suspend fun shouldPublishFixed(post: ScheduledPostWithMedia): Boolean {
        val app = applicationContext as AutoInstaApp
        val postId = post.post.id
        val action = ScheduleCalculator.actionFor(
            scheduledAtMillis = post.post.scheduledAt,
            policy = post.post.missedPolicy,
            nowMillis = System.currentTimeMillis(),
        )
        return when (action) {
            is ScheduleCalculator.Action.WaitUntil -> false // too early; the alarm stands
            ScheduleCalculator.Action.AskUser -> false      // the queue shows it; the owner decides
            ScheduleCalculator.Action.MarkMissed -> {
                fail(postId, post.post.caption, "Missed its time by more than the grace period.")
                app.notifier.notifyFailed(postId, post.post.caption, "Too late to post automatically")
                false
            }
            ScheduleCalculator.Action.PublishNow -> true
        }
    }

    /**
     * A queued post: it holds a place, not an appointment.
     *
     * Past its catch-up window it is **not** failed — it keeps its position and the
     * planner hands it the next slot. Marking it FAILED would punish the owner for the
     * phone having been off, and quietly drop a finished piece out of the rotation.
     */
    private suspend fun shouldPublishQueued(post: ScheduledPostWithMedia): Boolean {
        val app = applicationContext as AutoInstaApp
        val queue = app.queueRepository

        // An alarm armed before the owner hit pause can still be in flight.
        if (queue.settings().paused) {
            queue.replan()
            return false
        }

        val action = QueuePlanner.actionForQueued(
            scheduledAtMillis = post.post.scheduledAt,
            nowMillis = System.currentTimeMillis(),
            catchUpWindowMillis = queue.catchUpWindowMillis(),
        )
        return when (action) {
            is QueuePlanner.QueuedAction.WaitUntil -> false
            QueuePlanner.QueuedAction.RollForward -> {
                queue.replan()
                false
            }
            QueuePlanner.QueuedAction.PublishNow -> true
        }
    }

    /**
     * Take a finished post out of the pool so everything behind it shuffles up.
     * A no-op for a fixed post, which was never in the pool.
     */
    private suspend fun leaveQueueIfQueued(post: ScheduledPostWithMedia) {
        if (post.post.timingMode != TimingMode.QUEUED) return
        (applicationContext as AutoInstaApp).queueRepository.removeFromQueue(post.post.id)
    }

    private suspend fun fail(postId: Long, caption: String, reason: String) {
        val app = applicationContext as AutoInstaApp
        app.postRepository.updateStatus(postId, PostStatus.FAILED)
        val post = app.postRepository.getById(postId) ?: return
        app.historyRepository.record(
            PostHistoryEntity(
                postId = postId,
                postType = post.post.postType,
                caption = caption,
                hashtags = post.post.hashtags,
                scheduledAt = post.post.scheduledAt,
                postedAt = System.currentTimeMillis(),
                status = PostStatus.FAILED,
                instagramMediaId = null,
                errorMessage = reason,
            )
        )
    }

    companion object {
        const val KEY_POST_ID = "postId"

        /**
         * How many times a transient failure is retried before giving up. Instagram
         * containers expire after 24 hours, so retrying indefinitely would eventually
         * be publishing something that no longer exists.
         */
        const val MAX_RETRIES = 4
        private const val WORK_NAME_PREFIX = "publish-post-"

        fun inputFor(postId: Long): Data = workDataOf(KEY_POST_ID to postId)

        /**
         * Queue the publish for [postId].
         *
         * `KEEP` on a unique name means a duplicate trigger — alarm plus boot catch-up,
         * say — does not publish the same post twice.
         */
        fun enqueue(context: Context, postId: Long) {
            val request = OneTimeWorkRequestBuilder<PostWorker>()
                .setInputData(inputFor(postId))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "$WORK_NAME_PREFIX$postId",
                androidx.work.ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context, postId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork("$WORK_NAME_PREFIX$postId")
        }
    }
}
