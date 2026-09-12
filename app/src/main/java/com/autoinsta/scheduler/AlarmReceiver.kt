package com.autoinsta.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autoinsta.AutoInstaApp
import com.autoinsta.data.repository.EventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Where a scheduled alarm lands.
 *
 * Deliberately does almost nothing: a broadcast receiver gets only a few seconds before
 * the system kills it, and it runs on the main thread. Uploading media would blow through
 * that budget instantly. So this hands off to [PostWorker] and returns.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_POST_DUE) return
        val postId = intent.getLongExtra(EXTRA_POST_ID, -1L)
        if (postId <= 0L) return

        val app = context.applicationContext as AutoInstaApp

        // `goAsync` holds the broadcast open just long enough to write one row. Without it
        // the receiver can return and be killed before the insert lands — and "the alarm
        // never fired" and "it fired and we lost the note" look identical afterwards,
        // which is the whole ambiguity this log exists to remove.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.eventLog.log(
                    EventLog.Category.ALARM,
                    "FIRED",
                    postId,
                    // How late it was is the number that exposes standby throttling.
                    "lateBy=${System.currentTimeMillis() -
                        intent.getLongExtra(EXTRA_SCHEDULED_AT, 0L)}ms",
                )
            } finally {
                pending.finish()
            }
        }

        PostWorker.enqueue(context.applicationContext, postId)
    }

    companion object {
        const val ACTION_POST_DUE = "com.autoinsta.action.POST_DUE"
        const val EXTRA_POST_ID = "postId"

        /** Carried so the receiver can record how late the alarm actually arrived. */
        const val EXTRA_SCHEDULED_AT = "scheduledAt"
    }
}
