package com.karmios.code.orggcalsync

import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.calendar.model.EventReminder
import com.google.api.services.calendar.model.Event as GcalEvent

class Changes private constructor(
    val create: List<GcalEvent>,
    val update: List<Pair<String, GcalEvent>>,
    val delete: List<String>
) {
    companion object {
        private const val HOUR = 60*60*1000

        fun from(orgEvents: List<OrgEvent>, gcalEvents: List<GcalEvent>): Changes {
            val create = mutableListOf<GcalEvent>()
            val update = mutableListOf<Pair<String, GcalEvent>>()

            val unusedGcalEvents = gcalEvents.toMutableList()
            for (orgEvent in orgEvents) {
                gcalEvents.find { it.summary.trim() == orgEvent.title.trim() }?.also { gcalEvent ->
                    unusedGcalEvents.remove(gcalEvent)
                    update.add(gcalEvent.id to orgEvent.asGcal)
                } ?: create.add(orgEvent.asGcal)
            }
            val delete = unusedGcalEvents.map { it.id }

            return Changes(create, update, delete)
        }

        private fun EventDate.toGcalDate(shiftIfDateTime: Int, shiftIfDate: Int): EventDateTime =
            EventDateTime().also {
                val (date, hasTime) = this
                if (hasTime)
                    it.dateTime = DateTime(date.timeInMillis + shiftIfDateTime * HOUR)
                else
                    it.date = DateTime(true, date.timeInMillis + shiftIfDate * HOUR, null)
            }

        private val OrgEvent.asGcal: GcalEvent
            get() = GcalEvent().also { event ->
                event.summary = this.title
                event.description = this.content
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
    }
}

