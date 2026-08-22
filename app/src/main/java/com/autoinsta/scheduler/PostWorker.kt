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
import com.autoinsta.domain.ScheduleCalculator
import com.autoinsta.domain.model.PostStatus
import java.io.File

/**
 * Does the actual publishing when a post comes due.
 *
 * **This phase it is a stub** — no Cloudinary, no Graph API. It walks the entire
 * pipeline except the network call, so the timing, persistence, and notification paths
 * are all real and proven before Phase 5 swaps in the publish itself.
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
        val action = ScheduleCalculator.actionFor(
            scheduledAtMillis = post.post.scheduledAt,
            policy = post.post.missedPolicy,
            nowMillis = System.currentTimeMillis(),
        )
        when (action) {
            is ScheduleCalculator.Action.WaitUntil -> return Result.success() // too early; alarm stands
            ScheduleCalculator.Action.AskUser -> return Result.success()      // queue shows it, user decides
            ScheduleCalculator.Action.MarkMissed -> {
                fail(postId, post.post.caption, "Missed its time by more than the grace period.")
                notifier.notifyFailed(postId, post.post.caption, "Too late to post automatically")
                return Result.success()
            }
            ScheduleCalculator.Action.PublishNow -> Unit // fall through
        }

        repository.updateStatus(postId, PostStatus.POSTING)

        // The Phase 2.5 media work is what makes this check meaningful: these are our own
        // files, so a missing one is a real problem rather than an expired permission.
        val missing = post.mediaItems.filterNot { File(it.localUri).canRead() }
        if (missing.isNotEmpty()) {
            val reason = "${missing.size} media file(s) are missing from storage."
            fail(postId, post.post.caption, reason)
            notifier.notifyFailed(postId, post.post.caption, reason)
            return Result.success()
        }

        // ── Phase 5 replaces this block with the real upload + publish ──
        val fakeMediaId = "stub-${System.currentTimeMillis()}"

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
                instagramMediaId = fakeMediaId,
                errorMessage = null,
            )
        )
        notifier.notifyWouldHavePosted(postId, post.post.caption, post.mediaItems.size)
        return Result.success()
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
