package com.karmios.code.orggcalsync.org

import com.karmios.code.orggcalsync.org.Org.OrgNodeInTree
import com.karmios.code.orggcalsync.utils.*
import com.orgzly.org.datetime.OrgDelay
import com.orgzly.org.datetime.OrgInterval
import org.apache.commons.validator.routines.EmailValidator
import org.apache.logging.log4j.LogManager
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Calendar

/**
 * Org event
 *
 * @property title Event title
 * @property content Event details
 * @property start Event start time
 * @property end Event end time
 * @property reminderOffset Offset (in minutes) from the start time for reminders
 * @property state Event's state, as configured with todoKeywords / doneKeywords
 * @property tags List of event's tags, including inherited
 * @property ownTags List of event's tags, not including inherited
 * @property location The location of the event
 * @constructor
 */
data class OrgEvent(
    val title: String,
    val content: String,
    val start: EventDate,
    val end: EventDate?,
    val reminderOffset: Int?,
    val state: String?,
    val tags: Set<String>,
    val ownTags: Set<String>,
    val location: String?,
    val repeat: OrgEventRepeat?,
    val attendees: List<String>,
    val nonce: String?,
    val color: String?
) {
    /**
     * @param config Configuration
     * @return Whether this event should be considered when processing changes
     */
    fun shouldBeIncluded(config: Config): Boolean = with(config) {
        val ignoreTagIntersect = ignoreTags.intersect(tags)
        val ignoreOwnTagIntersect = ignoreOwnTags.intersect(ownTags)
        return when {
            ignoreTodos && state in todoKeywords -> {
                logger.debug("Ignoring '$title' because of $state (TODO-like) status")
                false
            }
            ignoreTagIntersect.isNotEmpty() -> {
                val tags = ignoreTagIntersect.joinToString(", ")
                logger.debug("Ignoring '$title' because tags include $tags")
                false
            }
            ignoreOwnTagIntersect.isNotEmpty() -> {
                val tags = ignoreOwnTagIntersect.joinToString(", ")
                logger.debug("Ignoring '$title' because own tags include $tags")
                false
            }
            includeTags.isNotEmpty() && includeTags.intersect(tags).isEmpty() -> {
                val tags = (if (includeTags.size > 1) "any of " else "") + includeTags.joinToString(", ")
                logger.debug("Ignoring '$title' because tags don't include $tags")
                false
            }
            includeOwnTags.isNotEmpty() && includeOwnTags.intersect(ownTags).isEmpty() -> {
                val tags = (if (includeOwnTags.size > 1) "any of " else "") + includeOwnTags.joinToString(", ")
                logger.debug("Ignoring '$title' because own tags don't include $tags")
                false
            }
            else -> true
        }
    }

    companion object {
        private val logger = LogManager.getLogger(OrgEvent::class.java.simpleName)

        private fun pickColor(tags: List<String>, config: Config): String? {
            config.colorMap.forEach { (requiredTags, color) ->
                if (tags.containsAll(requiredTags.split(" ")))
                    return color.toString()
            }
            return null
        }

        private fun buildAttendees(rawAttendees: String?, title: String, config: Config): List<String> =
            (rawAttendees ?: "").split(",").map { a ->
                a.trim().let { config.attendeeNicknames[it] ?: it }
            }.filter {
                EmailValidator.getInstance().isValid(it)
                    .also { isValid -> if (!isValid) logger.warn("Ignoring invalid attendee '$it' of '$title'") }
            }

        private fun fromOrg(node: OrgNodeInTree, ends: Map<String, EventDate>, config: Config): OrgEvent? {
            val head = node.head
            if ("end" in head.tags) return null
            val scheduled = head.scheduled ?: return null

            val title = head.title.trim()
            val timeZoneId = head.properties["TIME_ZONE"]?.toTimeZoneId()
            val startDate = scheduled.startTime ?: return null
            val start = startDate.toEventDate(config.timeZoneId, timeZoneId)
            val end = startDate.endCalendar?.toZonedDateTime(config.timeZoneId)?.let {
                if (timeZoneId != null)
                    it.withZoneSameLocal(timeZoneId) to true
                else
                    it to true
            }
                ?: scheduled.endTime?.toEventDate(config.timeZoneId, timeZoneId)
                ?: ends[title]

            return OrgEvent(
                title,
                head.content.trim(),
                start,
                end,
                startDate.delay?.let { getDelayInMinutes(startDate.calendar, it, config.zoneOffset) },
                head.state,
                node.inheritedTags.toSet(),
                head.tags.toSet(),
                head.properties["LOCATION"],
                OrgEventRepeat.fromOrg(head, start, end, timeZoneId ?: config.timeZoneId),
                buildAttendees(head.properties["ATTENDEES"], title, config),
                head.properties["NONCE"],
                pickColor(head.tags, config),
            )
        }

        private fun buildListFrom(nodes: List<OrgNodeInTree>, config: Config): List<OrgEvent> {
            val ends = nodes.mapNotNull { node ->
                val head = node.head
                if ("end" in head.tags) {
                    val timeZoneId = head.properties["TIME_ZONE"]?.toTimeZoneId()
                    head.scheduled?.startTime
                        ?.toEventDate(config.timeZoneId, timeZoneId)
                        ?.let { head.title.trim() to it }
                } else null
            }.toMap()
            return nodes.mapNotNull { fromOrg(it, ends, config) }
                .also { logger.debug("Found org events: " + it.joinToString(", ") { e -> e.title }) }
        }

        /**
         * Builds a list of events from an org-mode tree
         *
         * @param tree An org tree
         * @param config Configuration
         */
        fun buildListFrom(tree: Org, config: Config) =
            buildListFrom(if (config.flatten) tree.flattened else tree.children, config)

        /**
         * Gets the reminder delay of an event, in minutes, if it exists
         *
         * @param time The event's start time
         * @param delay The reminder delay, in org-mode format
         * @return The reminder delay in minutes
         */
        private fun getDelayInMinutes(time: Calendar, delay: OrgDelay, zoneOffset: ZoneOffset): Int? {
            return when (delay.unit) {
                OrgInterval.Unit.HOUR -> delay.value * 60
                OrgInterval.Unit.DAY -> delay.value * 60*24
                OrgInterval.Unit.WEEK -> delay.value * 60*24*7
                OrgInterval.Unit.MONTH -> run {
                    val epochSecond = time.timeInMillis / 1000
                    val delayTime = LocalDateTime.ofEpochSecond(epochSecond, 0, zoneOffset)
                        .minusDays(delay.value.toLong())
                    (epochSecond - delayTime.toEpochSecond(zoneOffset)).toInt()
                }
                else -> null
            }
        }
    }
}
