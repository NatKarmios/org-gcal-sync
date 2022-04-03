package com.karmios.code.orggcalsync

import com.karmios.code.orggcalsync.org.OrgEvent
import com.karmios.code.orggcalsync.utils.*
import org.apache.logging.log4j.LogManager
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

            val unusedGcalEvents = gcalEvents.toMutableList()
            val sortedGcalEvents = gcalEvents.sortedByDescending { (it.start.date ?: it.start.dateTime!!).value }
            val sortedOrgEvents = orgEvents.sortedByDescending { it.start.first }
                .map { it to it.toGcal(config.zoneOffset) }

            for ((orgEventRaw, orgEvent) in sortedOrgEvents) {
                sortedGcalEvents.find {
                    it.summary == orgEvent.summary && it in unusedGcalEvents
                }?.also { gcalEvent ->
                    unusedGcalEvents.remove(gcalEvent)
                    if (!(orgEvent eq gcalEvent)) {
                        update.add(gcalEvent.id to orgEvent)
                        logger.debug("Will update '${orgEvent.summary}'")
                    } else {
                        logger.debug("Event '${orgEvent.summary}' is up to date")
                    }
                } ?: run {
                    if (config.createEventsMarkedAsDone || orgEventRaw.state !in config.doneKeywords)
                        create.add(orgEvent).also { logger.debug("Will create '${orgEvent.summary}'") }
                }
            }
            val now = System.currentTimeMillis()
            val endThresh = now + config.deleteGracePeriod * HOUR
            val delete = unusedGcalEvents
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
