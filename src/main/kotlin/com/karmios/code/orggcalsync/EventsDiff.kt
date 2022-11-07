package com.karmios.code.orggcalsync

import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventReminder
import com.karmios.code.orggcalsync.org.OrgEvent
import com.karmios.code.orggcalsync.org.OrgEventRepeat
import com.karmios.code.orggcalsync.utils.*
import com.orgzly.org.datetime.OrgInterval
import org.apache.logging.log4j.LogManager
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.google.api.services.calendar.model.Event as GcalEvent

/**
 * Changes
 *
 * @property create List of event data to be created as Google calendar events
 * @property update List of Google event IDs with relevant update data
 * @property delete List of Google event IDs to be deleted
 */
class EventsDiff private constructor(
    val create: List<GcalEvent>,
    val update: List<Pair<String, GcalEvent>>,
    val delete: List<Pair<String, String>>
) {
    companion object {
        private val logger = LogManager.getLogger(EventsDiff::class.java.simpleName)

        private fun GcalEvent.addNonce(orgEvent: OrgEvent) {
            orgEvent.nonce ?: return

            this.extendedProperties = Event.ExtendedProperties().also {
                it.private = mapOf("nonce" to orgEvent.nonce)
            }
        }

        private fun GcalEvent.addReminder(orgEvent: OrgEvent) {
            this.reminders = GcalEvent.Reminders().also {
                it.useDefault = false
                val reminder = orgEvent.reminderOffset?.let { offset ->
                    EventReminder().also { reminder ->
                        reminder.method = "popup"
                        reminder.minutes = offset
                    }
                }
                it.overrides = listOfNotNull(reminder)
            }
        }

        private fun GcalEvent.addRepeat(repeat: OrgEventRepeat, defaultZoneId: ZoneId) {
            this.start = repeat.originalStart.toGcalDate(0, 12)
            this.end = repeat.originalEnd?.toGcalDate(0, 36)
                ?: repeat.originalStart.toGcalDate(1, 36)

            val frequency = when (repeat.repeatUnit) {
                OrgInterval.Unit.HOUR -> "HOURLY"
                OrgInterval.Unit.DAY -> "DAILY"
                OrgInterval.Unit.WEEK -> "WEEKLY"
                OrgInterval.Unit.MONTH -> "MONTHLY"
                OrgInterval.Unit.YEAR -> "YEARLY"
            }
            val repeatRules = mutableListOf<String>()
            val interval = repeat.repeatInterval.let {if (it != 1) ";INTERVAL=$it" else "" }
            val count = repeat.numRepeats?.let { ";COUNT=$it" } ?: ""
            repeatRules.add("RRULE:FREQ=$frequency$interval$count")

            val exDates = repeat.exclude.map { exDate ->
                exDate.withZoneSameInstant(defaultZoneId)
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))!!
            }
            if (exDates.isNotEmpty())
                repeatRules.add("EXDATE:" + exDates.joinToString(","))
            this.recurrence = repeatRules.toList()
        }

        /**
         * Converted to GcalEvent
         */
        private fun OrgEvent.toGcal(defaultZoneId: ZoneId) =
            GcalEvent().also { event ->
                event.summary = this.title.trim()
                event.description = this.content.trim()
                event.start = this.start.toGcalDate(0, 12)
                event.end = this.end?.toGcalDate(0, 36)
                    ?: this.start.toGcalDate(1, 36)
                event.location = this.location

                event.addNonce(this)
                event.addReminder(this)
                this.repeat?.let { event.addRepeat(it, defaultZoneId) }
            }

        /**
         * Creates a Changes object from org-mode and Google Calendar events
         *
         * @param orgEvents org-mode events
         * @param gcalEvents Google Calendar events
         * @param config Configuration
         * @return Newly-created Changes object
         */
        fun from(orgEvents: List<OrgEvent>, gcalEvents: List<GcalEvent>, config: Config): EventsDiff {
            val create = mutableListOf<GcalEvent>()
            val update = mutableListOf<Pair<String, GcalEvent>>()

            val sortedGcalEvents = gcalEvents.sortedByDescending { (it.start.date ?: it.start.dateTime!!).value }
                .toMutableList()
            val sortedOrgEvents = orgEvents.sortedByDescending { it.start.first }
                .map { it to it.toGcal(config.timeZoneId) }

            sortedOrgEvents.forEach { (orgEventRaw, orgEvent) ->
                val gcalEvent = if (orgEventRaw.nonce !== null)
                    sortedGcalEvents.findAndRemove { gcalEvent ->
                        gcalEvent.extendedProperties?.private?.get("nonce") == orgEventRaw.nonce
                    } else sortedGcalEvents.findAndRemove {
                        it.summary == orgEvent.summary
                    }

                if (gcalEvent != null) {
                    if (!(orgEvent eq gcalEvent)) {
                        update.add(gcalEvent.id to orgEvent)
                        logger.debug("Will update '${orgEvent.summary}'")
                    } else {
                        logger.debug("Event '${orgEvent.summary}' is up to date")
                    }
                } else {
                    if (config.createEventsMarkedAsDone || orgEventRaw.state !in config.doneKeywords)
                        create.add(orgEvent).also { logger.debug("Will create '${orgEvent.summary}'") }
                }
            }

            val now = System.currentTimeMillis()
            val endThresh = now + (config.deleteGracePeriod * HOUR) + (config.zoneOffset.totalSeconds * 1000)
            val delete = sortedGcalEvents
                .partition { endThresh < it.endMillis }
                .let { (deleteEvents, inGracePeriod) ->
                    inGracePeriod.forEach { logger.debug("Not deleting '${it.summary}' in grace period") }
                    deleteEvents.onEach { logger.debug("Will delete '${it.summary}'") }
                        .map { it.id to it.summary }
                }

            return EventsDiff(create, update, delete)
        }

        private infix fun GcalEvent?.eq(that: GcalEvent?) = isEq(this, that, logger)
    }
}
