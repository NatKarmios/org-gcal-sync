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

/**
 * Clamp an integer such that it lies between certain bounds
 *
 * @param lower Upper bound
 * @param upper Lower bound
 */
fun Int.clamp(lower: Int, upper: Int) = when {
    this < lower -> lower
    this > upper -> upper
    else -> this
}

/**
 * The given string, with '~' expanded to the current user's home directory
 */
val String.expanded
    get() = if (this.matches("""^~[/\\].+""".toRegex())) {
        System.getProperty("user.home") + this.substring(1)
    } else this

/**
 * The given date, converted to milliseconds since epoch
 */
fun LocalDate.toMillis(zoneOffset: ZoneOffset) =
    this.toEpochSecond(LocalTime.MIDNIGHT, zoneOffset) * 1000

// <editor-fold desc="Org">

/**
 * Converted to EventDate
 */
val OrgDateTime.asEventDate
    get() = this.calendar to this.hasTime()

/**
 * Converted to GcalEvent
 */
val OrgEvent.asGcal
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

/**
 * Converts to a GcalDate
 *
 * @param shiftIfDateTime Time offset (in hours) if the date includes time
 * @param shiftIfDate Time offset (in hours) if the date *does not* include time
 */
fun EventDate.toGcalDate(shiftIfDateTime: Int, shiftIfDate: Int) =
    EventDateTime().also {
        val (date, hasTime) = this
        if (hasTime)
            it.dateTime = DateTime(date.timeInMillis + shiftIfDateTime * HOUR)
        else
            it.date = DateTime(true, date.timeInMillis + shiftIfDate * HOUR, null)
    }

/**
 * The end time of the event, in epoch milliseconds
 */
val GcalEvent.endMillis
    get() = this.end.dateTime?.value
        ?: this.end.date.value

/**
 * Deep equality based on org-relevant fields
 */
infix fun GcalEvent?.eq(that: GcalEvent?): Boolean {
    if (this == null && that == null) return true
    if (this == null || that == null) return false

    return this.summary ?: "" == that.summary ?: ""
            && this.description ?: "" == that.description ?: ""
            && this.start.display == that.start.display
            && this.end.display == that.end.display
            && this.reminders eq that.reminders
}

/**
 * Clean string representation of an EventDateTime - used for easy equality check
 */
val EventDateTime.display: String
    get() = (this.date ?: this.dateTime!!).toString()

/**
 * Deep equality based on org-relevant fields
 */
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

/**
 * Redirect-able print stream
 *
 * @constructor
 *
 * @param ps The "default" output; usually System.out or System.err
 */
class RedirectedPrintStream(ps: PrintStream) : PrintStream(ps) {
    /**
     * Redirects output to the given target
     *
     * @param target The redirect target
     */
    fun redirectTo(target: PrintStream?) {
        redirectTarget = target
    }

    /**
     * Resets output to the "default"
     */
    fun reset() = redirectTo(null)

    private var redirectTarget: PrintStream? = null
    override fun write(buf: ByteArray, off: Int, len: Int) {
        redirectTarget?.write(buf, off, len)
            ?: super.write(buf, off, len)
    }
}

/**
 * Sets log4j2 log level
 *
 * Due to a quirk of log4j2, a complaint about class loading may be printed to System.out;
 * a RedirectedPrintStream can suppress this.
 *
 * @param level Desired log level
 * @param out Redirect-able output
 * @return Console output while setting log level
 */
fun setLogLevel(level: Level, out: RedirectedPrintStream): String {
    val buf = ByteArrayOutputStream()
    PrintStream(buf, true, UTF_8).use {
        out.redirectTo(it)
        Configurator.setAllLevels(LogManager.getRootLogger().name, level)
        out.reset()
    }
    return buf.toString(UTF_8)
}

/**
 * Trace action
 *
 * @param R Return type
 * @param msg Log message
 * @param f Code to run
 * @receiver Logger
 * @return Value returned from `f`
 */
fun <R> Logger.traceAction(msg: String, f: () -> R): R {
    this.trace("${msg.capitalized}...")
    val result = f()
    this.trace( "Done $msg.")
    return result
}

/**
 * Capitalized version of the string
 *
 * Apparently the built-in capitalize() is deprecated, weird decision.
 */
private val String.capitalized
    get() = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

/**
 * Indents each line of the string
 *
 * @param n Indent amount
 * @param ch Indent character
 */
fun String.indent(n: Int = 2, ch: String = " ") =
    this.split(System.lineSeparator()).joinToString(System.lineSeparator()) { ch.repeat(n) + it }

// </editor-fold>
