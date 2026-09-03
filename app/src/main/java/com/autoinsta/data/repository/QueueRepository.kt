package com.autoinsta.data.repository

import com.autoinsta.data.db.dao.PostingSlotDao
import com.autoinsta.data.db.dao.QueueSettingsDao
import com.autoinsta.data.db.dao.ScheduledPostDao
import com.autoinsta.data.db.entities.PostingSlotEntity
import com.autoinsta.data.db.entities.QueueSettingsEntity
import com.autoinsta.data.db.relations.ScheduledPostWithMedia
import com.autoinsta.domain.QueuePlanner
import com.autoinsta.scheduler.PostScheduler
import java.time.DayOfWeek
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The pool of posts waiting their turn, and the schedule that empties it.
 *
 * ## The one thing to hold on to
 * `queuePosition` is the truth for order. Every `scheduledAt` on a queued post is
 * **derived** here by [QueuePlanner] and rewritten on every [replan]. Nothing outside
 * this class should ever set a queued post's time.
 *
 * ## Why a mutex
 * [replan] is triggered from six places — app launch, any queue edit, a schedule change,
 * a finished publish, device boot, and a daily maintenance job. Two of them overlapping
 * would interleave "write the times" with "arm the alarms" and leave posts pointing at
 * moments no longer in the plan.
 */
class QueueRepository(
    private val postDao: ScheduledPostDao,
    private val slotDao: PostingSlotDao,
    private val settingsDao: QueueSettingsDao,
    private val postScheduler: PostScheduler,
    /** Injected so tests can say "pretend it is Wednesday evening in Tokyo". */
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {

    private val mutex = Mutex()

    // ── Observe ────────────────────────────────────────────────────────────

    /** The pool, in the owner's order. */
    fun observeQueue(): Flow<List<ScheduledPostWithMedia>> = postDao.observeQueued()

    /** Posts pinned to a time by hand — shown separately, never reordered. */
    fun observeFixedScheduled(): Flow<List<ScheduledPostWithMedia>> =
        postDao.observeFixedScheduled()

    fun observeSlots(): Flow<List<PostingSlotEntity>> = slotDao.observeAll()

    fun observeSettings(): Flow<QueueSettingsEntity> =
        settingsDao.observe().map { it ?: QueueSettingsEntity() }

    // ── Read ───────────────────────────────────────────────────────────────

    suspend fun settings(): QueueSettingsEntity = settingsDao.get() ?: QueueSettingsEntity()

    suspend fun catchUpWindowMillis(): Long =
        QueuePlanner.windowMillis(settings().catchUpWindowMinutes)

    /**
     * What a post added right now would get: a time, whether that time is a slot that
     * has already passed, and what the alternative would be if the owner would rather
     * wait for the next one.
     */
    suspend fun previewForNewPost(): QueuePreview {
        val now = clock()
        val zoneId = zone()
        val current = settings()
        val slots = enabledSlots()

        if (current.paused || slots.isEmpty()) {
            return QueuePreview(
                atMillis = null,
                isCatchUp = false,
                position = (postDao.getQueuedIdsInOrder().size) + 1,
                nextSlotAfterMillis = null,
                paused = current.paused,
                hasSlots = slots.isNotEmpty(),
            )
        }

        val existing = postDao.getQueuedIdsInOrder()
        val plan = QueuePlanner.plan(
            queuedIdsInOrder = existing + PREVIEW_POST_ID,
            slots = slots,
            nowMillis = now,
            zone = zoneId,
            catchUpWindowMillis = QueuePlanner.windowMillis(current.catchUpWindowMinutes),
            paused = false,
            resumedAtMillis = current.resumedAtMillis,
            fixedPostTimes = postDao.getFixedScheduledTimes(),
            notBefore = holds(),
            filledSlotTimes = filledSlots(now, current.catchUpWindowMinutes),
        )

        val at = plan.timeFor(PREVIEW_POST_ID)
        return QueuePreview(
            atMillis = at,
            isCatchUp = plan.isCatchUp(PREVIEW_POST_ID),
            position = existing.size + 1,
            nextSlotAfterMillis = at?.let {
                QueuePlanner.slotTimesFrom(slots, maxOf(it, now), zoneId).firstOrNull()
            },
            paused = false,
            hasSlots = true,
        )
    }

    // ── Write ──────────────────────────────────────────────────────────────

    /** Put a post at the back of the pool — where a piece you just finished belongs. */
    suspend fun addToQueue(postId: Long) {
        mutex.withLock {
            val next = (postDao.maxQueuePosition() ?: -1) + 1
            postDao.updateQueuePosition(postId, next)
        }
        replan()
    }

    /**
     * Commit a drag. Positions are rewritten 0..n-1 wholesale rather than nudged —
     * the pool is small, and a full rewrite cannot leave a gap or a duplicate behind.
     */
    suspend fun reorder(orderedIds: List<Long>) {
        mutex.withLock {
            orderedIds.forEachIndexed { index, id -> postDao.updateQueuePosition(id, index) }
        }
        replan()
    }

    /**
     * Take a post out of the pool — it published, or failed for good, or the owner gave
     * it a fixed time instead. Everything behind it shuffles up on the next replan.
     */
    suspend fun removeFromQueue(postId: Long) {
        mutex.withLock {
            postDao.clearQueuePosition(postId)
        }
        replan()
    }

    // ── The schedule ───────────────────────────────────────────────────────

    suspend fun addSlot(dayOfWeek: DayOfWeek, hour: Int, minute: Int): Long {
        val id = slotDao.insert(
            PostingSlotEntity(
                dayOfWeek = dayOfWeek.value,
                hourOfDay = hour,
                minute = minute,
            )
        )
        replan()
        return id
    }

    suspend fun updateSlot(slot: PostingSlotEntity) {
        slotDao.update(slot)
        replan()
    }

    suspend fun deleteSlot(slotId: Long) {
        slotDao.deleteById(slotId)
        replan()
    }

    suspend fun setPaused(paused: Boolean) {
        val current = settings()
        settingsDao.upsert(
            current.copy(
                paused = paused,
                // Stamped on resume so slots that went by during the pause are never
                // caught up afterwards — pausing means "do not post".
                resumedAtMillis = if (!paused) clock() else current.resumedAtMillis,
            )
        )
        replan()
    }

    suspend fun setCatchUpWindow(minutes: Int) {
        settingsDao.upsert(settings().copy(catchUpWindowMinutes = minutes))
        replan()
    }

    // ── The plan ───────────────────────────────────────────────────────────

    /**
     * Recompute every queued post's time and re-arm the alarms that matter.
     *
     * Alarms are armed only for what is close ([QueuePlanner.alarmsToArm]); everything
     * further out has a displayed date and nothing armed until a later replan brings it
     * into range.
     *
     * A post the planner could not place — the queue is paused, or has no slots — keeps
     * whatever `scheduledAt` it last had, but its alarm is cancelled, so the stale value
     * is inert. The UI reads [QueueSettingsEntity.paused] and the slot list to decide
     * what to show instead of a date.
     */
    suspend fun replan(): QueuePlanner.Plan = mutex.withLock {
        val now = clock()
        val current = settings()
        val slots = enabledSlots()

        val plan = QueuePlanner.plan(
            queuedIdsInOrder = postDao.getQueuedIdsInOrder(),
            slots = slots,
            nowMillis = now,
            zone = zone(),
            catchUpWindowMillis = QueuePlanner.windowMillis(current.catchUpWindowMinutes),
            paused = current.paused,
            resumedAtMillis = current.resumedAtMillis,
            fixedPostTimes = postDao.getFixedScheduledTimes(),
            notBefore = holds(),
            filledSlotTimes = filledSlots(now, current.catchUpWindowMinutes),
        )

        plan.assignments.forEach { postDao.updateScheduledAt(it.postId, it.atMillis) }

        val toArm = QueuePlanner.alarmsToArm(plan, now).associateBy { it.postId }
        plan.assignments.forEach { assignment ->
            val armed = toArm[assignment.postId]
            if (armed != null) {
                postScheduler.schedule(assignment.postId, armed.atMillis, now)
            } else {
                postScheduler.cancel(assignment.postId)
            }
        }
        plan.unassigned.forEach { postScheduler.cancel(it) }

        plan
    }

    // ── Internal ───────────────────────────────────────────────────────────

    private suspend fun enabledSlots(): List<QueuePlanner.Slot> =
        slotDao.getEnabled().map { it.toSlot() }

    private suspend fun holds(): Map<Long, Long> =
        postDao.getNotBeforeHolds().associate { it.id to it.notBeforeMillis }

    /**
     * Slots already used, going back as far as the catch-up window can reach. Anything
     * older cannot be offered anyway, so there is no point loading it.
     */
    private suspend fun filledSlots(nowMillis: Long, windowMinutes: Int): Set<Long> =
        postDao.getFilledSlotTimes(nowMillis - QueuePlanner.windowMillis(windowMinutes))
            .toSet()

    private companion object {
        /**
         * Stands in for the post being composed, which has no row yet. Negative so it
         * can never collide with a real auto-generated id.
         */
        const val PREVIEW_POST_ID = -1L
    }
}

/** What the compose screen shows about where a new post would land. */
data class QueuePreview(
    /** Null when the queue cannot place it — paused, or no slots defined. */
    val atMillis: Long?,
    /** True when [atMillis] is a slot that has already passed but is still open. */
    val isCatchUp: Boolean,
    /** 1-based, for display. */
    val position: Int,
    /** The "wait for the next slot instead" answer to a catch-up. */
    val nextSlotAfterMillis: Long?,
    val paused: Boolean,
    val hasSlots: Boolean,
)

fun PostingSlotEntity.toSlot(): QueuePlanner.Slot =
    QueuePlanner.Slot(
        dayOfWeek = DayOfWeek.of(dayOfWeek),
        hour = hourOfDay,
        minute = minute,
    )
