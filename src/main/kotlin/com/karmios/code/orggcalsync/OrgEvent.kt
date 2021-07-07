package com.karmios.code.orggcalsync

import com.orgzly.org.OrgHead
import com.orgzly.org.datetime.OrgDateTime
import com.orgzly.org.datetime.OrgDelay
import com.orgzly.org.datetime.OrgInterval
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Calendar

data class OrgEvent(
    val title: String,
    val content: String,
    val start: EventDate,
    val end: EventDate? = null,
    val reminderOffset: Int? = null
) {
    companion object {
        private fun fromOrg(head: OrgHead, ends: Map<String, EventDate>): OrgEvent? {
            if ("end" in head.tags) return null
            val start = head.scheduled?.startTime
                ?: return null
            return OrgEvent(
                head.title.trim(),
                head.content.trim(),
                start.calendar to start.hasTime(),
                start.endCalendar?.let { it to true }
                    ?: head.scheduled?.endTime
                        ?.asEventDate
                    ?: ends[head.title.trim()],
                head.scheduled?.startTime?.delay?.let { getDelayInMinutes(start.calendar, it) }
            )
        }

        private fun buildListFrom(heads: List<OrgHead>): List<OrgEvent> {
            val ends = heads.mapNotNull { head ->
                if ("end" in head.tags) {
                    head.scheduled?.startTime
                        ?.asEventDate
                        ?.let { head.title.trim() to it }
                } else null
            }.toMap()
            return heads.mapNotNull { fromOrg(it, ends) }
        }

        fun buildListFrom(tree: Org) = buildListFrom(tree.children.map { it.head })

        private fun getDelayInMinutes(time: Calendar, delay: OrgDelay): Int? {
            return when (delay.unit) {
                OrgInterval.Unit.HOUR -> delay.value * 60
                OrgInterval.Unit.DAY -> delay.value * 60*24
                OrgInterval.Unit.WEEK -> delay.value * 60*24*7
                OrgInterval.Unit.MONTH -> run {
                    val epochSecond = time.timeInMillis / 1000
                    val delayTime = LocalDateTime.ofEpochSecond(epochSecond, 0, ZoneOffset.UTC)
                        .minusDays(delay.value.toLong())
                    (epochSecond - delayTime.toEpochSecond(ZoneOffset.UTC)).toInt()
                }
                else -> null
            }
        }
    }
}
