package com.autoinsta.scheduler

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.autoinsta.AutoInstaApp
import java.util.concurrent.TimeUnit

/**
 * Keeps the Instagram login alive without the user having to open the app.
 *
 * ## Why this is needed
 * Meta's access token lasts 60 days and can be traded for a fresh 60 at any point before
 * it lapses — but a token that lapses is **gone permanently**, and the only remedy is
 * logging in by hand.
 *
 * Refreshing on app launch alone is a poor fit for this app in particular: the whole point
 * of autoinsta is that you set up posts and *stop opening it*. Someone could queue a
 * month of posts, never launch the app again, and have the login quietly die — with the
 * first symptom being posts failing.
 *
 * So the renewal runs on a weekly background check as well. Weekly against a 60-day
 * window leaves an enormous margin: the token would have to miss roughly eight
 * consecutive attempts before it was in any danger.
 */
class TokenRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as AutoInstaApp
        if (!app.accountRepository.isConnected()) return Result.success()

        // A no-op unless a refresh is actually due, and it never throws — a failure here
        // must not surface to the user, because the existing token is still valid.
        app.accountRepository.refreshIfNeeded()

        // Always success: retrying aggressively would achieve nothing (Meta refuses a
        // refresh on a token under 24 hours old) and the next weekly run is soon enough.
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "instagram-token-refresh"

        /**
         * Idempotent — safe to call on every launch. `KEEP` means an already-scheduled
         * job keeps its existing cadence rather than being reset each time the app opens,
         * which would defeat the point for someone who opens it rarely.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TokenRefreshWorker>(7, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
