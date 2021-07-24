package com.karmios.code.orggcalsync

import com.orgzly.org.OrgHead
import com.orgzly.org.parser.OrgNode
import com.orgzly.org.parser.OrgNodeInList
import com.orgzly.org.parser.OrgParser
import org.apache.logging.log4j.LogManager
import java.io.File
import java.util.*

sealed interface Org {
    val children: List<OrgNodeInTree>
    val inheritedTags: List<String>

    companion object {
        private val logger = LogManager.getLogger(Org::class.java.simpleName)

        private fun loadHeadsFrom(fileName: String, config: Config): List<OrgNodeInList> =
            OrgParser.Builder()
                .setInput(File(fileName).bufferedReader())
                .setTodoKeywords(config.stateKeywords.todo.toSet())
                .setDoneKeywords(config.stateKeywords.done.toSet())
                .build()
                .also { logger.info("Reading and parsing org from '$fileName'") }
                .parse()
                .headsInList

        fun load(config: Config): OrgRoot = OrgRoot(loadHeadsFrom(config.orgFile, config), config)
    }

    class OrgRoot private constructor(nodes: Queue<OrgNode>, private val config: Config) : Org {
        override val children: List<OrgNodeInTree>
        private val logger = LogManager.getLogger(OrgRoot::class.java.simpleName)

        init {
            val children = mutableListOf<OrgNodeInTree>()
            while (nodes.isNotEmpty())
                children.add(OrgNodeInTree(nodes.poll(), nodes, this))
            this.children = children
        }

        constructor(nodes: List<OrgNode>, config: Config) : this(LinkedList(nodes) as Queue<OrgNode>, config)

        override val inheritedTags: List<String> = emptyList()

        private fun findNodeAt(path: String) : OrgNodeInTree? {
            var node: OrgNodeInTree? = null
            for (desiredTitle in path.split("/").filter { it.isNotEmpty() }.map { it.trim() })
                node = (node ?: this).children.find { it.head.title.trim() == desiredTitle } ?: return null
            return node
        }

        private fun findEventsAt(path: String) : List<OrgEvent>? {
            logger.debug("Finding event headlines at '$path'")
            val node = findNodeAt(path) ?: return null
            return OrgEvent.buildListFrom(node, config)
        }

        fun findEvents(): List<OrgEvent> =
            findEventsAt(config.orgEventsPath)?.filter { it.shouldBeIncluded(config, logger) }
            ?: throw IllegalArgumentException("Couldn't find event parent node at '${config.orgEventsPath}'!")
    }

    class OrgNodeInTree (node: OrgNode, nodes: Queue<OrgNode>, private val parent: Org) : Org {
        val head: OrgHead = node.head
        override val children: List<OrgNodeInTree>

        init {
            val children = mutableListOf<OrgNodeInTree>()
            while (nodes.peek()?.level ?: 0 > node.level)
                children.add(OrgNodeInTree(nodes.remove(), nodes, this))
            this.children = children
        }

        override val inheritedTags: List<String> by lazy {
            head.tags + parent.inheritedTags
        }
    }
}
