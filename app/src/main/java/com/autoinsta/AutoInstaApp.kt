package com.autoinsta

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.autoinsta.data.db.AppDatabase
import com.autoinsta.data.media.MediaFileStore
import com.autoinsta.data.prefs.TokenStore
import com.autoinsta.data.remote.NetworkModule
import com.autoinsta.data.repository.AccountRepository
import com.autoinsta.data.repository.HistoryRepository
import com.autoinsta.data.repository.PostRepository
import com.autoinsta.data.repository.PresetRepository
import com.autoinsta.data.repository.CoachRepository
import com.autoinsta.data.repository.EventLog
import com.autoinsta.scheduler.BackgroundHealth
import com.autoinsta.data.repository.PublishRepository
import com.autoinsta.data.repository.QueueRepository
import com.autoinsta.scheduler.Notifier
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.autoinsta.scheduler.PostScheduler
import com.autoinsta.scheduler.QueueMaintenanceWorker
import com.autoinsta.scheduler.TokenRefreshWorker

/**
 * Application entry point. Owns the DB singleton and repository instances.
 *
 * Accessed via `(context.applicationContext as AutoInstaApp).postRepository` etc.
 * A proper DI framework (Hilt) is a candidate for a later phase; for now this
 * manual pattern keeps things minimal and dependency-free.
 */
class AutoInstaApp : Application(), ImageLoaderFactory {

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

    /**
     * The pool of posts waiting their turn, and the schedule that empties it.
     * The only thing allowed to set a queued post's time.
     */
    val queueRepository: QueueRepository by lazy {
        QueueRepository(
            postDao = database.scheduledPostDao(),
            slotDao = database.postingSlotDao(),
            settingsDao = database.queueSettingsDao(),
            postScheduler = postScheduler,
            eventLog = eventLog,
        )
    }

    /** The durable record of what the app did, for diagnosing what it did wrong. */
    val eventLog: EventLog by lazy { EventLog(dao = database.appEventDao()) }

    val presetRepository: PresetRepository by lazy {
        PresetRepository(presetDao = database.hashtagPresetDao())
    }

    /** The Instagram access token, encrypted at rest (not in Room — see TokenStore). */
    val tokenStore: TokenStore by lazy { TokenStore(this) }

    val accountRepository: AccountRepository by lazy {
        AccountRepository(
            accountDao = database.accountDao(),
            tokenStore = tokenStore,
            authApi = NetworkModule.instagramAuthApi,
        )
    }

    private val realPublishRepository: PublishRepository by lazy {
        PublishRepository(
            uploader = NetworkModule.cloudinaryUploader,
            api = NetworkModule.instagramApi,
            accountRepository = accountRepository,
            eventLog = eventLog,
        )
    }

    /**
     * Set by instrumented tests to keep them off the network.
     *
     * Without this, running the device test suite would publish test content to the
     * owner's live Instagram account. A test must never be able to do that.
     */
    @androidx.annotation.VisibleForTesting
    var publishRepositoryOverride: PublishRepository? = null

    /** Puts a scheduled post on Instagram. Used by PostWorker when a post comes due. */
    val publishRepository: PublishRepository
        get() = publishRepositoryOverride ?: realPublishRepository

    /**
     * The caption coach. Optional by construction — with no API key configured it reports
     * itself unavailable and the UI simply doesn't offer it.
     */
    val coachRepository: CoachRepository by lazy {
        CoachRepository(
            api = NetworkModule.anthropicApi,
            historyDao = database.postHistoryDao(),
            mediaFileStore = mediaFileStore,
        )
    }

    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(historyDao = database.postHistoryDao())
    }

    override fun onCreate() {
        super.onCreate()
        // Cheap and idempotent; must exist before any notification is posted.
        notifier.ensureChannel()

        // Instagram tokens die permanently after 60 days without a refresh. Two safety
        // nets, because this app is designed to be left alone:
        //   1. every launch — free, and covers the person who does open the app
        //   2. a weekly background job — covers the person who does not
        applicationScope.launch { accountRepository.refreshIfNeeded() }
        TokenRefreshWorker.schedule(this)

        // The queue's plan goes stale on its own: slots pass, catch-up windows close,
        // and posts come inside the alarm horizon. Replanning on launch covers the
        // person who opens the app; the daily job covers the one who does not.
        applicationScope.launch { queueRepository.replan() }
        QueueMaintenanceWorker.schedule(this)

        applicationScope.launch {
            // The standby bucket is why the 2026-09-09 slot never fired: in RARE, Android
            // throttles even setExactAndAllowWhileIdle to roughly once a day. Recording it
            // at every launch turns "the alarm didn't go off" from a guess into a reading.
            eventLog.log(
                EventLog.Category.APP,
                "LAUNCHED",
                detail = "standbyBucket=${BackgroundHealth.standbyBucket(this@AutoInstaApp)} " +
                    "batteryExempt=${BackgroundHealth.isIgnoringBatteryOptimisations(this@AutoInstaApp)} " +
                    "exactAlarms=${postScheduler.canScheduleExact()}",
            )
            eventLog.prune()
        }
    }

    /**
     * Coil's app-wide loader, taught to read a frame out of a video.
     *
     * Without [VideoFrameDecoder] every Reel is a grey film icon, which makes a queue of
     * timelapses impossible to tell apart — and telling them apart is the whole point of
     * being able to reorder them.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()

    /**
     * Lives as long as the process — for work that must not die with a screen.
     *
     * The handler is not decoration. `SupervisorJob` stops one child's failure killing its
     * siblings, but an exception with nowhere to go still reaches the thread's default
     * handler and takes the whole process down. Everything launched here is background
     * upkeep — a token refresh, a replan — and none of it is worth crashing the app in
     * front of the owner for.
     */
    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            Log.e("AutoInstaApp", "Background work failed", error)
        }
    )
}
