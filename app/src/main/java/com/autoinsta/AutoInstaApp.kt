package com.autoinsta

import android.app.Application
import com.autoinsta.data.db.AppDatabase
import com.autoinsta.data.media.MediaFileStore
import com.autoinsta.data.repository.AccountRepository
import com.autoinsta.data.repository.HistoryRepository
import com.autoinsta.data.repository.PostRepository
import com.autoinsta.data.repository.PresetRepository

/**
 * Application entry point. Owns the DB singleton and repository instances.
 *
 * Accessed via `(context.applicationContext as AutoInstaApp).postRepository` etc.
 * A proper DI framework (Hilt) is a candidate for a later phase; for now this
 * manual pattern keeps Phase 1 minimal and dependency-free.
 */
class AutoInstaApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    /** Owns the copies of picked photos/videos kept in app-private storage. */
    val mediaFileStore: MediaFileStore by lazy { MediaFileStore(this) }

    val postRepository: PostRepository by lazy {
        PostRepository(
            postDao = database.scheduledPostDao(),
            mediaDao = database.mediaItemDao(),
            mediaFileStore = mediaFileStore,
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
        // TODO(Phase 3): create notification channel + WorkManager config
    }
}
