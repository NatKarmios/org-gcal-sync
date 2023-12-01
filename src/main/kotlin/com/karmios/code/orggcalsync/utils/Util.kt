package com.karmios.code.orggcalsync.utils

import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Event as GcalEvent
import com.google.api.services.calendar.model.EventDateTime
import com.orgzly.org.datetime.OrgDateTime
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.config.Configurator
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

import kotlin.text.replaceFirstChar


const val HOUR = 60*60*1000

// <editor-fold desc="Primitives">

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

val String?.nullIfBlank
    get() = if (this.isNullOrBlank()) null else this

// </editor-fold>

// <editor-fold desc="Time/Date">

typealias EventDate = Pair<ZonedDateTime, Boolean>

fun String.toTimeZoneId(): ZoneId? =
    if (this in TimeZone.getAvailableIDs())
        TimeZone.getTimeZone(this).toZoneId()
    else null

/**
 * The given date, converted to milliseconds since epoch
 */
fun LocalDate.toMillis(zoneOffset: ZoneOffset) =
    this.toEpochSecond(LocalTime.MIDNIGHT, zoneOffset) * 1000

fun String.toZonedDateTimeOrNull(fmt: DateTimeFormatter) = try {
    ZonedDateTime.parse(this, fmt)
} catch (_: DateTimeParseException) {
    null
}

fun String.toLocalDateOrNull(fmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE) = try {
    LocalDate.parse(this, fmt)
} catch (_: DateTimeParseException) {
    null
}

fun String.toLocalDateTimeOrNull(fmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME) = try {
    LocalDateTime.parse(this, fmt)
} catch (_: DateTimeParseException) {
    null
}

fun String.toEventDateOrNull(defaultTimeZone: ZoneId) =
    toZonedDateTimeOrNull(DateTimeFormatter.ISO_OFFSET_DATE)?.let { it to false }
    ?: toZonedDateTimeOrNull(DateTimeFormatter.ISO_OFFSET_DATE_TIME)?.let { it to true }
    ?: toLocalDateOrNull()?.let { it.atStartOfDay(defaultTimeZone) to false }
    ?: toLocalDateTimeOrNull()?.let { it.atZone(defaultTimeZone) to true }

fun Calendar.toZonedDateTime(zoneId: ZoneId) =
    ZonedDateTime.ofInstant(this.toInstant(), this.timeZone.toZoneId())!!
        .withZoneSameLocal(zoneId)!!

fun OrgDateTime.toEventDate(defaultZoneId: ZoneId, zoneId: ZoneId? = null) =
    this.calendar.toZonedDateTime(defaultZoneId)
        .let { if (zoneId != null) it.withZoneSameLocal(zoneId) else it  }
        .let { it to this.hasTime() }

// </editor-fold>

/**
 * Gets a file from the jar resources, with a fallback local file
 */
fun readResource(path: String, fallbackPath: String) =
    object {}.javaClass.getResourceAsStream(path)
        ?.reader()?.readText()
        ?: File(fallbackPath).readText()

fun <T> MutableList<T>.findAndRemove(pred: (T) -> Boolean): T? = indexOfFirst(pred).let { ix ->
    if (ix != -1)
        this.removeAt(ix)
    else null
}

// <editor-fold desc="Gcal">

/**
 * Converts to a GcalDate
 *
 * @param shiftIfDateTime Time offset (in hours) if the date includes time
 * @param shiftIfDate Time offset (in hours) if the date *does not* include time
 */
fun EventDate.toGcalDate(shiftIfDateTime: Int, shiftIfDate: Int): EventDateTime {
    val (date, hasTime) = this

    val gcalDate = EventDateTime()
    val millis = date.toInstant().toEpochMilli()
    val tzOffsetMinutes = date.offset.totalSeconds / 60

    if (hasTime)
        gcalDate.dateTime = DateTime(millis + shiftIfDateTime * HOUR, tzOffsetMinutes)
    else
        gcalDate.date = DateTime(true, millis + shiftIfDate * HOUR, tzOffsetMinutes)

    gcalDate.timeZone = date.zone.id

    return gcalDate
}

/**
 * The end time of the event, in epoch milliseconds
 */
val GcalEvent.endMillis
    get() = this.end.dateTime?.value
        ?: this.end.date.value

/**
 * Clean string representation of an EventDateTime - used for easy equality check
 */
val EventDateTime.display: String
    get() = this.date?.toString() ?: this.dateTime!!.toStringRfc3339()

// </editor-fold>

// <editor-fold desc="Logging / Printing">

/**
 * Redirect-able print stream
 *
 * @constructor
 *
 * @param ps The "default" output; usually `System.out` or `System.err`
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
