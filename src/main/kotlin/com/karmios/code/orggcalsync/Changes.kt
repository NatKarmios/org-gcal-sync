package com.karmios.code.orggcalsync

import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.calendar.model.EventReminder
import org.apache.logging.log4j.LogManager
import com.google.api.services.calendar.model.Event as GcalEvent

class Changes private constructor(
    val create: List<GcalEvent>,
    val update: List<Pair<String, GcalEvent>>,
    val delete: List<String>
) {
    companion object {
        private const val HOUR = 60*60*1000
        private val logger = LogManager.getLogger(Changes::class.java)

        fun from(orgEvents: List<OrgEvent>, gcalEvents: List<GcalEvent>): Changes {
            val create = mutableListOf<GcalEvent>()
            val update = mutableListOf<Pair<String, GcalEvent>>()

            val unusedGcalEvents = gcalEvents.toMutableList()
            for (orgEvent in orgEvents.map { it.asGcal }) {
                gcalEvents.find { it.summary == orgEvent.summary }?.also { gcalEvent ->
                    unusedGcalEvents.remove(gcalEvent)
                    if (!(orgEvent eq gcalEvent)) {
                        update.add(gcalEvent.id to orgEvent)
                        logger.debug("Will update '${orgEvent.summary}'")
                    } else {
                        logger.debug("Event '${orgEvent.summary}' is up to date")
                    }
                } ?: create.add(orgEvent).also { logger.debug("Will create '${orgEvent.summary}'") }
            }
            val delete = unusedGcalEvents.map { logger.debug("Will delete '${it.summary}'"); it.id }

            return Changes(create, update, delete)
        }

        private val OrgEvent.asGcal: GcalEvent
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

        private fun EventDate.toGcalDate(shiftIfDateTime: Int, shiftIfDate: Int): EventDateTime =
            EventDateTime().also {
                val (date, hasTime) = this
                if (hasTime)
                    it.dateTime = DateTime(date.timeInMillis + shiftIfDateTime * HOUR)
                else
                    it.date = DateTime(true, date.timeInMillis + shiftIfDate * HOUR, null)
            }

        private infix fun GcalEvent?.eq(that: GcalEvent?): Boolean {
            if (this == null && that == null) return true
            if (this == null || that == null) return false

            return this.summary ?: "" == that.summary ?: ""
                    && this.description ?: "" == that.description ?: ""
                    && (this.start?.date == that.start?.date || this.start?.dateTime == that.start?.dateTime)
                    && (this.end?.date == that.end?.date || this.end?.dateTime == that.end?.dateTime)
                    && this.reminders eq that.reminders
        }

        private infix fun GcalEvent.Reminders?.eq(that: GcalEvent.Reminders?): Boolean {
            if (this == null && that == null) return true
            if (this == null || that == null) return false

            val aOverrides = this.overrides ?: emptyList()
            val bOverrides = that.overrides ?: emptyList()

            return this.useDefault == that.useDefault
                    && aOverrides.size == bOverrides.size
                    && aOverrides.sortedBy { it.minutes }.zip(bOverrides.sortedBy { it.minutes })
                        .all { (a, b) -> a.minutes == b.minutes && a.method == b.method }
        }
    }
}
