package com.karmios.code.orggcalsync

import com.sksamuel.hoplite.ConfigLoader
import java.io.File

data class Config(
    val orgFile: String,
    val orgEventsPath: String,
    val calendarId: String,
    val credentialFile: String = "./credentials.json",
    val flatten: Boolean = false,
    val ignoreTodos: Boolean = false,
    val createEventsMarkedAsDone: Boolean = false,
    private val todoKeywords: String = "TODO WAIT STRT PROJ",
    private val doneKeywords: String = "DONE KILL",
    val deleteGracePeriod: Int = 24,
    val ignoreTags: List<String> = emptyList(),
    val ignoreOwnTags: List<String> = emptyList(),
) {
    val stateKeywords = StateKeywords(todoKeywords, doneKeywords)

    data class StateKeywords(val todo: List<String>, val done: List<String>) {
        constructor(todo: String, done: String) : this(todo.split(" "), done.split(" "))
    }

    companion object {
        fun load(fileName: String) = ConfigLoader().loadConfigOrThrow<Config>(File(fileName))
    }
}
