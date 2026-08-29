package com.autoinsta.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autoinsta.AutoInstaApp
import com.autoinsta.data.db.entities.ScheduledPostEntity
import com.autoinsta.data.repository.MediaToSave
import com.autoinsta.domain.model.MediaType
import com.autoinsta.domain.model.PostStatus
import com.autoinsta.domain.model.PostType
import com.autoinsta.domain.model.TimingMode
import java.io.File
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The drag gesture itself, on a real screen.
 *
 * The *arithmetic* behind it is covered on the JVM by `DragReorderTest` — which index the
 * finger is over, and what moving an element does to a list. What cannot be checked there
 * is whether the gesture is wired to that arithmetic at all: whether a drag reaches the
 * handler, and whether releasing writes the new order to the database. That is this test.
 *
 * It runs against the real database, so everything it creates is torn down again —
 * including the owner's own posting schedule, which is saved and restored.
 */
@RunWith(AndroidJUnit4::class)
class QueueReorderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: AutoInstaApp
    private lateinit var sourceDir: File
    private val createdPostIds = mutableListOf<Long>()
    private val createdSlotIds = mutableListOf<Long>()
    private var savedPaused = false

    @Before
    fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        sourceDir = File(app.cacheDir, "reorder-test").apply { mkdirs() }
        savedPaused = app.queueRepository.settings().paused
        app.queueRepository.setPaused(false)
        // Somewhere for the planner to put things, so the cards show real dates.
        createdSlotIds += app.queueRepository.addSlot(DayOfWeek.MONDAY, 19, 0)
    }

    @After
    fun tearDown() = runBlocking {
        createdPostIds.forEach { app.postRepository.deletePost(it) }
        createdPostIds.clear()
        createdSlotIds.forEach { app.queueRepository.deleteSlot(it) }
        createdSlotIds.clear()
        app.queueRepository.setPaused(savedPaused)
        sourceDir.deleteRecursively()
    }

    private suspend fun givenQueuedPost(caption: String): Long {
        val file = File(sourceDir, "$caption.jpg").apply { writeBytes(ByteArray(64) { 7 }) }
        val id = app.postRepository.insertPost(
            ScheduledPostEntity(
                postType = PostType.SINGLE_IMAGE,
                status = PostStatus.SCHEDULED,
                caption = caption,
                hashtags = "",
                presetId = null,
                scheduledAt = System.currentTimeMillis() + DAY_MILLIS,
                timingMode = TimingMode.QUEUED,
                createdAt = System.currentTimeMillis(),
            ),
            listOf(
                MediaToSave(
                    sourceUri = "file://${file.absolutePath}",
                    mediaType = MediaType.IMAGE,
                    alreadyImported = false,
                )
            ),
        )
        createdPostIds += id
        app.queueRepository.addToQueue(id)
        return id
    }

    private suspend fun queueCaptions(): List<String> =
        app.queueRepository.observeQueue().first().map { it.post.caption }

    @Test
    fun draggingTheLastCardToTheTopReordersThePoolAndPersistsIt() = runBlocking {
        val first = givenQueuedPost("first")
        val second = givenQueuedPost("second")
        val third = givenQueuedPost("third")

        assertEquals(listOf("first", "second", "third"), queueCaptions())

        composeRule.setContent {
            HomeScreen(
                onOpenSettings = {},
                onOpenSchedule = {},
                onCreatePost = {},
                onEditPost = {},
            )
        }
        composeRule.waitForIdle()

        val handles = composeRule.onAllNodesWithContentDescription("Drag to reorder")
        handles[2].performTouchInput {
            down(center)
            // Far enough up to clear every card above it. The target index clamps to the
            // topmost visible row, so the exact distance does not have to be tuned.
            moveBy(Offset(0f, -1_200f))
            up()
        }
        composeRule.waitForIdle()

        assertEquals(
            "the dragged post should now be first, and it should have been saved",
            listOf("third", "first", "second"),
            queueCaptions(),
        )

        // The times follow the order, not the other way round.
        val queue = app.queueRepository.observeQueue().first()
        assertEquals(third, queue[0].post.id)
        assertEquals(first, queue[1].post.id)
        assertEquals(second, queue[2].post.id)
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
