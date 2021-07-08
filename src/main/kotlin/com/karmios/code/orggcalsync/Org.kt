package com.karmios.code.orggcalsync

import com.orgzly.org.OrgHead
import com.orgzly.org.parser.OrgNode
import com.orgzly.org.parser.OrgNodeInList
import com.orgzly.org.parser.OrgParser
import java.io.File
import java.util.*

sealed interface Org {
    val children: List<OrgNodeInTree>
    val inheritedTags: List<String>

    companion object {
        private fun loadHeadsFrom(fileName: String, ): List<OrgNodeInList> =
            OrgParser.Builder()
                .setInput(File(fileName).bufferedReader())
                .setTodoKeywords(setOf("TODO", "WAIT", "PROJ", "STRT"))
                .setDoneKeywords(setOf("DONE", "KILL"))
                .build()
                .parse()
                .headsInList

        fun load(config: Config): OrgRoot = OrgRoot(loadHeadsFrom(config.orgFile), config)
    }

    class OrgRoot private constructor(nodes: Queue<OrgNode>, private val config: Config) : Org {
        override val children: List<OrgNodeInTree>

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
            val node = findNodeAt(path) ?: return null
            return OrgEvent.buildListFrom(node)
        }

        fun findEvents(config: Config = this.config): List<OrgEvent>? = findEventsAt(config.orgEventsPath)
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
