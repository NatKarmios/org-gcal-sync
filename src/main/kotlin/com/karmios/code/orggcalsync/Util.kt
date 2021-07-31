package com.karmios.code.orggcalsync

import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Event as GcalEvent
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.calendar.model.EventReminder
import com.orgzly.org.datetime.OrgDateTime
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.config.Configurator
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import java.time.*
import java.util.*

import kotlin.text.replaceFirstChar


typealias EventDate = Pair<Calendar, Boolean>

const val HOUR = 60*60*1000

fun Int.clamp(lower: Int, upper: Int): Int = when {
    this < lower -> lower
    this > upper -> upper
    else -> this
}

val String.expanded: String
    get() = if (this.matches("""^~[/\\].+""".toRegex())) {
        System.getProperty("user.home") + this.substring(1)
    } else this

val LocalDate.millis: Long
    get() = this.toEpochSecond(LocalTime.MIDNIGHT, ZoneOffset.UTC) * 1000

// <editor-fold desc="Org">

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

// </editor-fold>

// <editor-fold desc="Gcal">

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

// </editor-fold>

// <editor-fold desc="Logging / Printing">

class RedirectedPrintStream(ps: PrintStream) : PrintStream(ps) {
    fun redirectTo(ps: PrintStream?) {
        redirectTarget = ps
    }

    fun reset() = redirectTo(null)

    private var redirectTarget: PrintStream? = null
    override fun write(buf: ByteArray, off: Int, len: Int) {
        redirectTarget?.write(buf, off, len)
            ?: super.write(buf, off, len)
    }
}

fun setLogLevel(level: Level, out: RedirectedPrintStream): String {
    val buf = ByteArrayOutputStream()
    PrintStream(buf, true, UTF_8).use {
        out.redirectTo(it)
        Configurator.setAllLevels(LogManager.getRootLogger().name, level)
        out.reset()
    }
    return buf.toString(UTF_8)
}

fun <T> Logger.traceAction(msg: String, f: () -> T): T {
    this.trace("${msg.capitalized}...")
    val result = f()
    this.trace( "Done $msg.")
    return result
}

private val String.capitalized
    get() = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

fun String.indent(n: Int = 2) =
    this.split(System.lineSeparator()).joinToString(System.lineSeparator()) { " ".repeat(n) + it }

// </editor-fold>
