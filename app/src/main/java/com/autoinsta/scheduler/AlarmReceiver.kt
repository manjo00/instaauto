package com.autoinsta.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

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

        PostWorker.enqueue(context.applicationContext, postId)
    }

    companion object {
        const val ACTION_POST_DUE = "com.autoinsta.action.POST_DUE"
        const val EXTRA_POST_ID = "postId"
    }
}
