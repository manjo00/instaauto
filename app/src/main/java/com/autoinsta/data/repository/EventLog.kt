package com.autoinsta.data.repository

import android.util.Log
import com.autoinsta.data.db.dao.AppEventDao
import com.autoinsta.data.db.entities.AppEventEntity

/**
 * A durable account of what the app did, so the next "why didn't it post?" has an answer.
 *
 * ## Why not just logcat
 * Logcat is a ring buffer. It is emptied by a reboot, overwritten within hours on a busy
 * device, and gone entirely by the time anyone notices a post was missed. Every interesting
 * thing this app does happens while nobody is watching — an alarm at 19:00, a worker
 * deciding to skip, a lease being taken. Working out the 2026-09-09 incident needed a
 * database pull and three `dumpsys` calls, and only worked because the tablet was to hand.
 *
 * So this writes to the database instead: ordered, durable, and readable days later by
 * `tools/diagnostics.ps1`.
 *
 * ## Never let the diary break the app
 * Every write is best-effort. A diagnostic that can fail a publish would be worse than no
 * diagnostic at all, so failures here are swallowed after being logged.
 */
class EventLog(
    private val dao: AppEventDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Coarse grouping, so a timeline can be filtered to one concern. */
    object Category {
        const val ALARM = "ALARM"
        const val WORKER = "WORKER"
        const val LEASE = "LEASE"
        const val PUBLISH = "PUBLISH"
        const val QUEUE = "QUEUE"
        const val APP = "APP"
        const val ACCOUNT = "ACCOUNT"
        const val COACH = "COACH"
    }

    suspend fun log(
        category: String,
        event: String,
        postId: Long? = null,
        detail: String? = null,
    ) {
        runCatching {
            dao.insert(
                AppEventEntity(
                    atMillis = clock(),
                    category = category,
                    event = event,
                    postId = postId,
                    detail = detail,
                )
            )
        }.onFailure { Log.w(TAG, "could not record $category/$event", it) }
    }

    suspend fun recent(limit: Int = 500): List<AppEventEntity> =
        runCatching { dao.recent(limit) }.getOrDefault(emptyList())

    /**
     * Drop anything past the retention window.
     *
     * Two weeks covers "it went wrong last Wednesday and I got to you on Friday", which is
     * the real reporting delay this has to survive, without letting the table grow forever.
     */
    suspend fun prune() {
        runCatching { dao.pruneOlderThan(clock() - RETENTION_MILLIS) }
            .onFailure { Log.w(TAG, "could not prune the event log", it) }
    }

    private companion object {
        const val TAG = "EventLog"
        const val RETENTION_MILLIS = 14L * 24 * 60 * 60 * 1000
    }
}
