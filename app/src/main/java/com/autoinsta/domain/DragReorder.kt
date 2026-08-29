package com.autoinsta.domain

/**
 * The arithmetic behind dragging a card to a new place in the queue.
 *
 * ## Why this is separate from the gesture
 * A drag is two things wearing one coat: a pointer being tracked, and a decision about
 * which index the finger is now over. The first genuinely needs a device. The second is
 * ordinary maths, and leaving it tangled in the composable would mean the only way to
 * check "does dragging past the last visible card clamp correctly" is to try it by hand
 * on a phone. Split out, that is a unit test.
 *
 * Bounds are in pixels, in the same coordinate space the caller measured them in --
 * this object never assumes which.
 */
object DragReorder {

    /** Where one row currently sits. [index] is its position in the full list. */
    data class ItemBounds(val index: Int, val top: Int, val bottom: Int)

    /**
     * The index the dragged item should move to, given where the finger is.
     *
     * [bounds] holds only the rows currently on screen, which is all a lazy list can
     * report. A finger dragged past either end therefore has nothing under it, and is
     * clamped to the nearest visible row rather than being ignored -- the list is
     * auto-scrolling at that point, so the next frame brings new rows into range.
     */
    fun targetIndexFor(
        fromIndex: Int,
        pointerY: Int,
        bounds: List<ItemBounds>,
    ): Int {
        if (bounds.isEmpty()) return fromIndex

        bounds.firstOrNull { pointerY >= it.top && pointerY < it.bottom }
            ?.let { return it.index }

        val highest = bounds.minByOrNull { it.top } ?: return fromIndex
        val lowest = bounds.maxByOrNull { it.bottom } ?: return fromIndex
        return if (pointerY < highest.top) highest.index else lowest.index
    }

    /**
     * Move one element, shifting everything between it and its destination.
     *
     * Out-of-range indices return the list untouched: a drag can outlive the list it
     * started on (a post publishes mid-gesture), and dropping the move is the only
     * outcome that cannot corrupt the order.
     */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from == to) return list
        if (from !in list.indices || to !in list.indices) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }
}
