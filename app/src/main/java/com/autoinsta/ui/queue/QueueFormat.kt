package com.autoinsta.ui.queue

import com.autoinsta.data.db.entities.PostingSlotEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * How the queue's times are written for a person to read.
 *
 * Deliberately in the UI layer, not in `domain/` — [com.autoinsta.domain.QueuePlanner]
 * deals in epoch millis and knows nothing about wording, which is what lets it be tested
 * without a locale and translated later without touching the rules.
 */

/** "Monday" in the phone's language. */
fun dayName(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.FULL, Locale.getDefault())

fun shortDayName(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

/** "7:00 PM", or "19:00" where that is what people use. */
fun timeOfDay(hour: Int, minute: Int): String =
    LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

/** "Monday · 7:00 PM" — how one slot reads in the list. */
fun slotLabel(slot: PostingSlotEntity): String =
    "${dayName(DayOfWeek.of(slot.dayOfWeek))} · ${timeOfDay(slot.hourOfDay, slot.minute)}"

/** "Wed, Sep 9 · 7:00 PM" — an actual moment, for a queue card or a preview. */
fun momentLabel(atMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(atMillis)
        .atZone(zone)
        .format(DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.getDefault()))

/** How the catch-up window is offered: "1 hour", "2 hours", "1 day", "2 days". */
fun windowLabel(minutes: Int): String = when (minutes) {
    60 -> "1 hour"
    120 -> "2 hours"
    1_440 -> "1 day"
    2_880 -> "2 days"
    else -> if (minutes % 1_440 == 0) "${minutes / 1_440} days" else "${minutes / 60} hours"
}

/** "1h ago", "3 days ago" — how late an open slot is, in the roughest useful terms. */
fun agoLabel(millisAgo: Long): String {
    val minutes = millisAgo / 60_000L
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        else -> "${minutes / (60 * 24)} days ago"
    }
}
