package com.autoinsta.scheduler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.autoinsta.R

/**
 * Everything the app tells the user while it is not on screen.
 *
 * A scheduling app that fails quietly is worse than useless — the whole promise is that
 * something happened while you weren't looking, so both outcomes have to be reported.
 *
 * Posting a notification is best-effort: on Android 13+ the user can refuse the
 * permission, and there is no sensible way to fail a publish because we could not
 * announce it. Every send is therefore guarded rather than assumed.
 */
class Notifier(
    private val context: Context,
) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Scheduled posts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Tells you when a scheduled post went out, or why it didn't."
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun notifyWouldHavePosted(postId: Long, caption: String, mediaCount: Int) {
        val what = if (mediaCount == 1) "1 file" else "$mediaCount files"
        show(
            id = postId,
            title = "Would have posted now",
            body = buildString {
                append(what)
                if (caption.isNotBlank()) append(" · ").append(caption.take(60))
            },
        )
    }

    fun notifyFailed(postId: Long, caption: String, reason: String) {
        show(
            id = postId,
            title = "Post didn't go out",
            body = if (caption.isBlank()) reason else "${caption.take(40)} — $reason",
        )
    }

    private fun show(id: Long, title: String, body: String) {
        // Checked inline rather than in a helper: lint can only prove the notify() call
        // below is guarded if the check is visible in the same method.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()

        // Even with the permission granted, the platform can still refuse; a failed
        // notification must never take down the publish that triggered it.
        runCatching {
            NotificationManagerCompat.from(context).notify(id.toInt(), notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "scheduled_posts"
    }
}
