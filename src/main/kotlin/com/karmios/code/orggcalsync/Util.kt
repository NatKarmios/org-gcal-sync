package com.karmios.code.orggcalsync

import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Event as GcalEvent
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.calendar.model.EventReminder
import com.orgzly.org.datetime.OrgDateTime
import java.time.*
import java.util.*


typealias EventDate = Pair<Calendar, Boolean>

const val HOUR = 60*60*1000

fun Int.clamp(lower: Int, upper: Int): Int = when {
    this < lower -> lower
    this > upper -> upper
    else -> this
}

val LocalDate.millis: Long
    get() = this.toEpochSecond(LocalTime.MIDNIGHT, ZoneOffset.UTC) * 1000

val OrgDateTime.asEventDate: EventDate
    get() = this.calendar to this.hasTime()

val OrgEvent.asGcal: GcalEvent
    get() = GcalEvent().also { event ->
        event.summary = this.title.trim()
        event.description = this.content.trim()
        event.start = this.start.toGcalDate(0, 12)
        event.end = this.end?.toGcalDate(0, 36)
            ?: this.start.toGcalDate(1, 36)
        event.reminders = GcalEvent.Reminders().also {
            it.useDefault = false
            val reminder = this.reminderOffset?.let { offset ->
                EventReminder().also { reminder ->
                    reminder.method = "popup"
                    reminder.minutes = offset
                }
            }
            it.overrides = listOfNotNull(reminder)
        }
    }

fun EventDate.toGcalDate(shiftIfDateTime: Int, shiftIfDate: Int): EventDateTime =
    EventDateTime().also {
        val (date, hasTime) = this
        if (hasTime)
            it.dateTime = DateTime(date.timeInMillis + shiftIfDateTime * HOUR)
        else
            it.date = DateTime(true, date.timeInMillis + shiftIfDate * HOUR, null)
    }

val GcalEvent.endMillis: Long
    get() = this.end.dateTime?.value
        ?: this.end.date.value + HOUR * 24

infix fun GcalEvent?.eq(that: GcalEvent?): Boolean {
    if (this == null && that == null) return true
    if (this == null || that == null) return false

    return this.summary ?: "" == that.summary ?: ""
            && this.description ?: "" == that.description ?: ""
            && (this.start?.date == that.start?.date || this.start?.dateTime == that.start?.dateTime)
            && (this.end?.date == that.end?.date || this.end?.dateTime == that.end?.dateTime)
            && this.reminders eq that.reminders
}

infix fun GcalEvent.Reminders?.eq(that: GcalEvent.Reminders?): Boolean {
    if (this == null && that == null) return true
    if (this == null || that == null) return false

    val aOverrides = this.overrides ?: emptyList()
    val bOverrides = that.overrides ?: emptyList()

    return this.useDefault == that.useDefault
            && aOverrides.size == bOverrides.size
            && aOverrides.sortedBy { it.minutes }.zip(bOverrides.sortedBy { it.minutes })
        .all { (a, b) -> a.minutes == b.minutes && a.method == b.method }
}