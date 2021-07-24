package com.karmios.code.orggcalsync

import com.karmios.code.orggcalsync.Org.OrgNodeInTree
import com.orgzly.org.datetime.OrgDelay
import com.orgzly.org.datetime.OrgInterval
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Calendar

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
    fun shouldBeIncluded(config: Config, logger: Logger? = null): Boolean {
        val tagIntersect = config.ignoreTags.intersect(tags)
        val ownTagIntersect = config.ignoreOwnTags.intersect(ownTags)
        return when {
            config.ignoreTodos && state in config.stateKeywords.todo -> {
                logger?.debug("Ignoring '$title' because of $state (TODO-like) status")
                false
            }
            tagIntersect.isNotEmpty() -> {
                val tags = tagIntersect.joinToString(", ")
                logger?.debug("Ignoring '$title' because tags include $tags")
                false
            }
            ownTagIntersect.isNotEmpty() -> {
                val tags = ownTagIntersect.joinToString(", ")
                logger?.debug("Ignoring '$title' because own tags include $tags")
                false
            }
            else -> true
        }
    }

    companion object {
        private val logger = LogManager.getLogger(OrgEvent::class.java.simpleName)

        private fun fromOrg(node: OrgNodeInTree, ends: Map<String, EventDate>): OrgEvent? {
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
                head.scheduled?.startTime?.delay?.let { getDelayInMinutes(start.calendar, it) },
                head.state,
                node.inheritedTags,
                head.tags.toList()
            )
        }

        private fun buildListFrom(nodes: List<OrgNodeInTree>): List<OrgEvent> {
            val ends = nodes.mapNotNull { node ->
                val head = node.head
                if ("end" in head.tags) {
                    head.scheduled?.startTime
                        ?.asEventDate
                        ?.let { head.title.trim() to it }
                } else null
            }.toMap()
            return nodes.mapNotNull { fromOrg(it, ends) }
                .also { logger.debug("Found org events: " + it.joinToString(", ") { e -> e.title }) }
        }

        fun buildListFrom(tree: Org, config: Config) = buildListFrom(tree.children)

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
