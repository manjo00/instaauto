package com.autoinsta.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.autoinsta.AutoInstaApp
import java.util.concurrent.TimeUnit

/**
 * Keeps the posting queue's plan honest while nobody is looking.
 *
 * ## Why a background job at all
 * The queue re-plans whenever something happens to it — the app opens, a post is added or
 * dragged, the schedule changes, a post publishes, the phone boots. That covers almost
 * everything, and it self-sustains: each publish triggers the next plan.
 *
 * The gap is the case where **nothing happens**. A pool left empty for three weeks
 * publishes nothing, so nothing triggers a replan, so alarms beyond the horizon are never
 * armed and a catch-up window that has expired is never cleared. Someone who queues a
 * month of work and stops opening the app is exactly this project's target user, so that
 * gap is not hypothetical.
 *
 * Daily is far more often than needed against a 7-day alarm horizon, and costs nothing.
 */
class QueueMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as AutoInstaApp
        app.queueRepository.replan()
        // Always success: a replan is pure bookkeeping over local data, and the next
        // daily run is soon enough for anything that did go wrong.
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "queue-maintenance"

        /**
         * Idempotent — safe on every launch. `KEEP` so an already-scheduled job holds its
         * cadence instead of being reset each time the app opens.
         *
         * No network constraint: this touches nothing but the local database and the
         * alarm manager.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<QueueMaintenanceWorker>(1, TimeUnit.DAYS)
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
