package com.autoinsta.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.autoinsta.domain.ScheduleCalculator

/**
 * Arms and cancels the alarms that wake the device when a post is due.
 *
 * ## Why an alarm *and* a worker
 * They solve different problems. An alarm is a precise doorbell: it wakes the device at
 * a specific moment, but hands you only a few seconds of execution and does not survive
 * a reboot. A WorkManager job is the opposite: it survives process death and reboots and
 * can retry, but its timing is loose. So the alarm's only job is to ring on time and
 * hand the real work to [PostWorker].
 *
 * ## Exact alarms are not free
 * Since Android 14, `SCHEDULE_EXACT_ALARM` is denied by default and cannot be granted by
 * a dialog — the user has to switch it on in Settings. When we do not have it, this class
 * degrades to an inexact alarm rather than failing: posts still go out, just approximately.
 * [canScheduleExact] lets the UI tell the user which of those two worlds they are in.
 *
 * `open` so tests that are not about alarms can substitute a no-op and avoid arming real
 * ones on the device — a test using a throwaway database would otherwise leave alarms
 * behind keyed on ids that collide with the user's real posts.
 */
open class PostScheduler(
    private val context: Context,
) {

    private val alarmManager: AlarmManager? = context.getSystemService()

    /**
     * True when the OS will honour to-the-minute alarms for us.
     * False means posts still fire, but may drift — the UI should say so.
     */
    open fun canScheduleExact(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true // pre-Android 12 exact alarms need no special access
        }
    }

    /**
     * Schedule [postId] to fire at [scheduledAtMillis].
     * Re-arming an existing post is safe: the PendingIntent is keyed on the post id, so
     * `FLAG_UPDATE_CURRENT` replaces the previous alarm rather than stacking a second one.
     */
    open fun schedule(postId: Long, scheduledAtMillis: Long, nowMillis: Long = System.currentTimeMillis()) {
        val manager = alarmManager ?: return
        val triggerAt = ScheduleCalculator.alarmTimeFor(scheduledAtMillis, nowMillis)
        val pendingIntent = pendingIntentFor(postId, mutable = false)

        if (canScheduleExact()) {
            // setExactAndAllowWhileIdle is the only variant that pierces Doze. Measured
            // budget on the test device: 72 firings/hour — far above this app's needs.
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            // No exact-alarm access: still wakes, but the OS may batch it with others.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    /** Cancel a post's alarm — on delete, or before re-arming at a new time. */
    open fun cancel(postId: Long) {
        val manager = alarmManager ?: return
        manager.cancel(pendingIntentFor(postId, mutable = false))
    }

    private fun pendingIntentFor(postId: Long, mutable: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_POST_DUE
            putExtra(AlarmReceiver.EXTRA_POST_ID, postId)
            // The extras are not part of PendingIntent equality, so without a distinct
            // data URI every post would share one PendingIntent and overwrite the others.
            data = android.net.Uri.parse("autoinsta://post/$postId")
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE

        return PendingIntent.getBroadcast(context, postId.toInt(), intent, flags)
    }
}
