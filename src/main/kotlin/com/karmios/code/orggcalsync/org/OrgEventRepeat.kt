package com.karmios.code.orggcalsync.org

import com.karmios.code.orggcalsync.utils.*
import com.orgzly.org.OrgHead
import com.orgzly.org.datetime.OrgInterval
import java.time.ZoneId
import java.time.ZonedDateTime

data class OrgEventRepeat(
    val originalStart: EventDate,
    val originalEnd: EventDate?,
    val repeatUnit: OrgInterval.Unit,
    val repeatInterval: Int,
    val numRepeats: Int?,
    val exclude: List<ZonedDateTime>
) {
    companion object {
        fun fromOrg(head: OrgHead, start: EventDate, end: EventDate?, zoneId: ZoneId): OrgEventRepeat? {
            val repeater = head.scheduled?.startTime?.repeater ?: return null
            val originalStart =
                head.properties["OG_START"]?.toEventDateOrNull(zoneId)
                ?: start
            val originalEnd =
                head.properties["OG_END"]?.toEventDateOrNull(zoneId)
                ?: end

            val repeatUnit = repeater.unit!!
            val interval = repeater.value

            val numRepeats =
                head.properties["COUNT"]?.let(String::toIntOrNull)
                ?: head.properties["UNTIL"]?.toEventDateOrNull(zoneId)?.let { (until, _) ->
                    val advance = when (repeatUnit) {
                        OrgInterval.Unit.HOUR -> ZonedDateTime::plusHours
                        OrgInterval.Unit.DAY -> ZonedDateTime::plusDays
                        OrgInterval.Unit.WEEK -> ZonedDateTime::plusWeeks
                        OrgInterval.Unit.MONTH -> ZonedDateTime::plusMonths
                        OrgInterval.Unit.YEAR -> ZonedDateTime::plusYears
                    }

                    var (currentTime, _) = originalStart
                    var count = 0
                    while (currentTime <= until) {
                        count++
                        currentTime = advance(currentTime, interval.toLong())
                    }

                    count
                }

            val excludeRaw = head.properties["EX_DATES"]?.split("|") ?: emptyList()
            val exclude = excludeRaw.mapNotNull {
                it.trim().toEventDateOrNull(zoneId)?.let { (date, hasTime) ->
                    if (hasTime)
                        date
                    else
                        date.withHour(start.first.hour)
                            .withMinute(start.first.minute)
                            .withSecond(start.first.second)
                }
            }

            return OrgEventRepeat(
                originalStart,
                originalEnd,
                repeater.unit,
                repeater.value,
                numRepeats,
                exclude
            )
        }
    }
}
