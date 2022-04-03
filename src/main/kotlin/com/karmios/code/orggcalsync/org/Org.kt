package com.karmios.code.orggcalsync.org

import com.github.kittinunf.fuel.httpGet
import com.karmios.code.orggcalsync.utils.Config
import com.orgzly.org.OrgHead
import com.orgzly.org.parser.OrgNode
import com.orgzly.org.parser.OrgNodeInList
import com.orgzly.org.parser.OrgParser
import org.apache.logging.log4j.LogManager
import java.io.File
import java.util.*

/**
 * A node in an tree representing an org-mode document
 */
sealed interface Org {
    /**
     * This node's direct children
     */
    val children: List<OrgNodeInTree>

    /**
     * This node's tags and those of all its ancestors
     */
    val inheritedTags: List<String>

    /**
     * This node and all its descendants, flattened into a list
     */
    val flattened: List<OrgNodeInTree>

    companion object {
        private val logger = LogManager.getLogger(Org::class.java.simpleName)

        private fun loadHeads(input: String, config: Config): List<OrgNodeInList> =
            OrgParser.Builder()
                .setInput(input)
                .setTodoKeywords(config.todoKeywords.toSet())
                .setDoneKeywords(config.doneKeywords.toSet())
                .build()
                .also { logger.trace("Parsing org data...") }
                .parse()
                .headsInList

        private fun loadInput(orgFile: String, isLocal: Boolean) =
            if (isLocal) {
                logger.trace("Reading org data from file...")
                File(orgFile).readText()
            } else {
                logger.trace("Fetching org data from URL...")
                val (_, _, result) = orgFile
                    .httpGet()
                    .responseString()
                result.get()
            }

        /**
         * Creates a tree from an org-mode file
         *
         * @param config Configuration
         * @return The newly-created tree
         */
        fun load(config: Config, orgData: String?): OrgRoot {
            if (orgData.isNullOrBlank() && config.orgFile.isNullOrBlank())
                throw IllegalArgumentException("No org file provided!")
            val input = orgData ?: loadInput(config.orgFile!!, config.localOrgFile)
            val heads = loadHeads(input, config)
            return OrgRoot(heads, config)
        }
    }

    /**
     * The root of an org-mode document
     */
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

        override val flattened: List<OrgNodeInTree>
            get() = children.flatMap { it.flattened }

        private fun findNodeAt(path: String) : OrgNodeInTree? {
            var node: OrgNodeInTree? = null
            for (desiredTitle in path.split("/").filter { it.isNotEmpty() }.map { it.trim() })
                node = (node ?: this).children.find { it.head.title.trim() == desiredTitle } ?: return null
            return node
        }

        private fun findEventsAt(path: String) : List<OrgEvent>? {
            logger.trace("Finding event headlines at '$path'...")
            val node = findNodeAt(path) ?: return null
            return OrgEvent.buildListFrom(node, config)
        }

        /**
         * @return A list of events from org-mode according to the config
         */
        fun findEvents(): List<OrgEvent> =
            findEventsAt(config.orgEventsPath)?.filter { it.shouldBeIncluded(config) }
            ?: throw IllegalArgumentException("Couldn't find event parent node at '${config.orgEventsPath}'!")
    }

    /**
     * A (non-root) node in an org-mode tree
     *
     * @property parent
     * @property head The org-mode headline data at this location
     */
    class OrgNodeInTree (node: OrgNode, nodes: Queue<OrgNode>, private val parent: Org) : Org {
        val head: OrgHead = node.head
        override val children: List<OrgNodeInTree>

        init {
            val children = mutableListOf<OrgNodeInTree>()
            while ((nodes.peek()?.level ?: 0) > node.level)
                children.add(OrgNodeInTree(nodes.remove(), nodes, this))
            this.children = children
        }

        override val inheritedTags: List<String> by lazy {
            head.tags + parent.inheritedTags
        }

        override val flattened: List<OrgNodeInTree>
            get() = listOf(this) + children.flatMap { it.flattened }
    }
}
