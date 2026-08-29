package com.autoinsta.domain

import com.autoinsta.domain.DragReorder.ItemBounds
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The maths behind dragging a card up or down the queue, checked without a phone.
 *
 * Rows are 100px tall starting at 0, so row N occupies [N*100, N*100+100).
 */
class DragReorderTest {

    private companion object {
        val ROWS = (0..4).map { ItemBounds(index = it, top = it * 100, bottom = it * 100 + 100) }
    }

    // ── Which row is the finger over ───────────────────────────────────────

    @Test
    fun `the finger inside a row targets that row`() {
        assertEquals(3, DragReorder.targetIndexFor(fromIndex = 0, pointerY = 350, bounds = ROWS))
    }

    @Test
    fun `the very top of a row already counts as that row`() {
        assertEquals(2, DragReorder.targetIndexFor(fromIndex = 0, pointerY = 200, bounds = ROWS))
    }

    @Test
    fun `the last pixel of a row still counts as that row`() {
        assertEquals(2, DragReorder.targetIndexFor(fromIndex = 0, pointerY = 299, bounds = ROWS))
    }

    @Test
    fun `staying put targets the row you started on`() {
        assertEquals(1, DragReorder.targetIndexFor(fromIndex = 1, pointerY = 150, bounds = ROWS))
    }

    // ── Dragged past the ends ──────────────────────────────────────────────

    @Test
    fun `dragging above the first visible row clamps to it`() {
        assertEquals(0, DragReorder.targetIndexFor(fromIndex = 3, pointerY = -500, bounds = ROWS))
    }

    @Test
    fun `dragging below the last visible row clamps to it`() {
        assertEquals(4, DragReorder.targetIndexFor(fromIndex = 0, pointerY = 9_000, bounds = ROWS))
    }

    @Test
    fun `clamping uses the visible rows, not the whole list`() {
        // A lazy list only reports what is on screen: rows 5..7 here.
        val scrolled = (5..7).map { ItemBounds(it, (it - 5) * 100, (it - 5) * 100 + 100) }

        assertEquals(5, DragReorder.targetIndexFor(fromIndex = 6, pointerY = -20, bounds = scrolled))
        assertEquals(7, DragReorder.targetIndexFor(fromIndex = 6, pointerY = 400, bounds = scrolled))
    }

    @Test
    fun `no visible rows leaves the item where it was`() {
        assertEquals(2, DragReorder.targetIndexFor(fromIndex = 2, pointerY = 100, bounds = emptyList()))
    }

    // ── Moving the element ─────────────────────────────────────────────────

    @Test
    fun `moving down shifts everything in between up`() {
        assertEquals(listOf("b", "c", "a", "d"), DragReorder.move(listOf("a", "b", "c", "d"), 0, 2))
    }

    @Test
    fun `moving up shifts everything in between down`() {
        assertEquals(listOf("a", "d", "b", "c"), DragReorder.move(listOf("a", "b", "c", "d"), 3, 1))
    }

    @Test
    fun `moving to the same place changes nothing`() {
        val list = listOf("a", "b", "c")
        assertEquals(list, DragReorder.move(list, 1, 1))
    }

    @Test
    fun `moving to the end works`() {
        assertEquals(listOf("b", "c", "a"), DragReorder.move(listOf("a", "b", "c"), 0, 2))
    }

    @Test
    fun `an out-of-range index drops the move rather than corrupting the order`() {
        val list = listOf("a", "b", "c")

        // A drag can outlive the list it started on — a post publishes mid-gesture.
        assertEquals(list, DragReorder.move(list, 0, 9))
        assertEquals(list, DragReorder.move(list, -1, 1))
        assertEquals(list, DragReorder.move(list, 1, 9))
        assertEquals(emptyList<String>(), DragReorder.move(emptyList<String>(), 0, 1))
    }
}
