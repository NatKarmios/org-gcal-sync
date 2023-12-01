package com.karmios.code.orggcalsync.utils

import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventAttendee
import com.google.api.services.calendar.model.EventDateTime
import org.apache.logging.log4j.Logger

private fun isNonceEq(a: Event, b: Event) =
    a.extendedProperties?.private?.get("nonce") ==
            b.extendedProperties?.private?.get("nonce")

private infix fun EventDateTime.eq(that: EventDateTime): Boolean {
    return this.display == that.display
}

/**
 * Deep equality based on org-relevant fields
 */
private infix fun Event.Reminders?.eq(that: Event.Reminders?): Boolean {
    if (this == null && that == null) return true
    if (this == null || that == null) return false

    val aOverrides = this.overrides ?: emptyList()
    val bOverrides = that.overrides ?: emptyList()

    return this.useDefault == that.useDefault
            && aOverrides.size == bOverrides.size
            && aOverrides.sortedBy { it.minutes }.zip(bOverrides.sortedBy { it.minutes })
        .all { (a, b) -> a.minutes == b.minutes && a.method == b.method }
}

/**
 * Deep equality based on org-relevant fields
 */
fun isEq(a: Event?, b: Event?, logger: Logger): Boolean {
    if (a == null && b == null) return true
    if (a == null || b == null) return false

    val conflictingProperties = listOf(
        ((a.summary ?: "") == (b.summary ?: "")) to "summary",
        ((a.description ?: "") == (b.description ?: "")) to "description",
        (a.start eq b.start) to "start time",
        (a.end eq b.end) to "end time",
        (a.reminders eq b.reminders) to "reminder list",
        (a.location == b.location) to "location",
        (isNonceEq(a, b)) to "nonce",
        (a.recurrence == b.recurrence) to "recurrence",
        (a.colorId == b.colorId) to "color id"

    ).mapNotNull { (isEq, property) -> if (isEq) null else property }

    return conflictingProperties.isEmpty().also { if (!it) {
        val name = a.summary ?: b.summary
        val conflicts = conflictingProperties.joinToString(", ")
        logger.debug("Event '$name' conflicts due to mismatched $conflicts")
    } }
}
