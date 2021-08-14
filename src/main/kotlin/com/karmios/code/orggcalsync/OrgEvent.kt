package com.karmios.code.orggcalsync

import com.karmios.code.orggcalsync.Org.OrgNodeInTree
import com.orgzly.org.datetime.OrgDelay
import com.orgzly.org.datetime.OrgInterval
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
 * @constructor
 */
data class OrgEvent(
    val title: String,
    val content: String,
    val start: EventDate,
    val end: EventDate?,
    val reminderOffset: Int?,
    val state: String?,
    val tags: List<String>,
    val ownTags: List<String>
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

        private fun fromOrg(node: OrgNodeInTree, ends: Map<String, EventDate>, config: Config): OrgEvent? {
            val head = node.head
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
                head.scheduled?.startTime?.delay?.let { getDelayInMinutes(start.calendar, it, config.zoneOffset) },
                head.state,
                node.inheritedTags,
                head.tags.toList()
            )
        }

        private fun buildListFrom(nodes: List<OrgNodeInTree>, config: Config): List<OrgEvent> {
            val ends = nodes.mapNotNull { node ->
                val head = node.head
                if ("end" in head.tags) {
                    head.scheduled?.startTime
                        ?.asEventDate
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
