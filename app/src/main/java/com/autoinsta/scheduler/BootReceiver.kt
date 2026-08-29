package com.autoinsta.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autoinsta.AutoInstaApp
import com.autoinsta.domain.ScheduleCalculator
import com.autoinsta.domain.model.TimingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-arms the queue after the device reboots.
 *
 * Alarms do **not** survive a restart — the OS forgets every one of them. Without this,
 * a phone that rebooted overnight would silently stop posting, and nothing in the app
 * would look wrong. That is the worst kind of failure for a scheduling app.
 *
 * Each pending post is re-evaluated rather than blindly re-armed, because time passed
 * while the device was off. Queued posts go through the planner, which re-derives their
 * times and grants at most one catch-up; fixed-time posts go through [ScheduleCalculator],
 * honouring their own per-post missed rule.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return

        val app = context.applicationContext as AutoInstaApp
        // goAsync() buys this receiver time to touch the database before the system
        // reclaims it — a plain coroutine launch here would race the process shutting down.
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()

                // The queue re-derives its own times and arms its own alarms, including
                // the one catch-up a phone that was off overnight is entitled to. Doing
                // it first means the fixed-post pass below sees the times it settled on.
                app.queueRepository.replan()

                app.postRepository.getAllScheduled()
                    .filter { it.post.timingMode == TimingMode.FIXED }
                    .forEach { item ->
                        when (ScheduleCalculator.actionFor(item.post.scheduledAt, item.post.missedPolicy, now)) {
                            is ScheduleCalculator.Action.WaitUntil ->
                                app.postScheduler.schedule(item.post.id, item.post.scheduledAt, now)

                            ScheduleCalculator.Action.PublishNow ->
                                PostWorker.enqueue(context.applicationContext, item.post.id)

                            // Both of these need the worker to record the outcome (mark
                            // FAILED / leave for the user) rather than being dropped here.
                            ScheduleCalculator.Action.MarkMissed,
                            ScheduleCalculator.Action.AskUser ->
                                PostWorker.enqueue(context.applicationContext, item.post.id)
                        }
                    }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            // Some OEMs (Samsung among them) send this instead after certain restarts.
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
