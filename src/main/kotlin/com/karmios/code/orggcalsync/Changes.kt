package com.karmios.code.orggcalsync

import org.apache.logging.log4j.LogManager
import com.google.api.services.calendar.model.Event as GcalEvent

class Changes private constructor(
    val create: List<GcalEvent>,
    val update: List<Pair<String, GcalEvent>>,
    val delete: List<Pair<String, String>>
) {
    companion object {
        private val logger = LogManager.getLogger(Changes::class.java.simpleName)

        fun from(orgEvents: List<OrgEvent>, gcalEvents: List<GcalEvent>, config: Config): Changes {
            val create = mutableListOf<GcalEvent>()
            val update = mutableListOf<Pair<String, GcalEvent>>()

            val unusedGcalEvents = gcalEvents.toMutableList()
            for ((orgEventRaw, orgEvent) in orgEvents.map { it to it.asGcal }) {
                gcalEvents.find { it.summary == orgEvent.summary }?.also { gcalEvent ->
                    unusedGcalEvents.remove(gcalEvent)
                    if (!(orgEvent eq gcalEvent)) {
                        update.add(gcalEvent.id to orgEvent)
                        logger.debug("Will update '${orgEvent.summary}'")
                    } else {
                        logger.debug("Event '${orgEvent.summary}' is up to date")
                    }
                } ?: run {
                    if (config.createEventsMarkedAsDone || orgEventRaw.state !in config.stateKeywords.done)
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

            return Changes(create, update, delete)
        }
    }
}
