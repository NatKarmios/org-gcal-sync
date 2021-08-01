package com.karmios.code.orggcalsync

import com.sksamuel.hoplite.ConfigLoader
import java.io.File

data class Config(
    val orgFile: String,
    val calendarId: String,
    val orgEventsPath: String = "",
    val credentialFile: String = "./credentials.json",
    val todoKeywords: List<String> = listOf("TODO", "WAIT", "STRT", "PROJ"),
    val doneKeywords: List<String> = listOf("DONE", "KILL"),
    val flatten: Boolean = false,
    val includeTags: List<String> = emptyList(),
    val includeOwnTags: List<String> = emptyList(),
    val ignoreTags: List<String> = emptyList(),
    val ignoreOwnTags: List<String> = emptyList(),
    val ignoreTodos: Boolean = false,
    val createEventsMarkedAsDone: Boolean = false,
    val deleteGracePeriod: Int = 24,
) {
    companion object {
        fun load(fileName: String) = ConfigLoader().loadConfigOrThrow<Config>(File(fileName.expanded))
    }
}
