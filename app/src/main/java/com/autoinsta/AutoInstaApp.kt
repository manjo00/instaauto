package com.autoinsta

import android.app.Application
import com.autoinsta.data.db.AppDatabase
import com.autoinsta.data.media.MediaFileStore
import com.autoinsta.data.repository.AccountRepository
import com.autoinsta.data.repository.HistoryRepository
import com.autoinsta.data.repository.PostRepository
import com.autoinsta.data.repository.PresetRepository
import com.autoinsta.scheduler.Notifier
import com.autoinsta.scheduler.PostScheduler

/**
 * Application entry point. Owns the DB singleton and repository instances.
 *
 * Accessed via `(context.applicationContext as AutoInstaApp).postRepository` etc.
 * A proper DI framework (Hilt) is a candidate for a later phase; for now this
 * manual pattern keeps things minimal and dependency-free.
 */
class AutoInstaApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    /** Owns the copies of picked photos/videos kept in app-private storage. */
    val mediaFileStore: MediaFileStore by lazy { MediaFileStore(this) }

    /** Arms and cancels the alarms that wake the device when a post is due. */
    val postScheduler: PostScheduler by lazy { PostScheduler(this) }

    /** Success/failure notifications for posts that fire while the app is closed. */
    val notifier: Notifier by lazy { Notifier(this) }

    val postRepository: PostRepository by lazy {
        PostRepository(
            postDao = database.scheduledPostDao(),
            mediaDao = database.mediaItemDao(),
            mediaFileStore = mediaFileStore,
            postScheduler = postScheduler,
        )
    }

    val presetRepository: PresetRepository by lazy {
        PresetRepository(presetDao = database.hashtagPresetDao())
    }

    val accountRepository: AccountRepository by lazy {
        AccountRepository(accountDao = database.accountDao())
    }

    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(historyDao = database.postHistoryDao())
    }

    override fun onCreate() {
        super.onCreate()
        // Cheap and idempotent; must exist before any notification is posted.
        notifier.ensureChannel()
    }
}
