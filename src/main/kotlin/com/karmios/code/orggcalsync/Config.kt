package com.karmios.code.orggcalsync

import com.sksamuel.hoplite.ConfigLoader
import java.io.File

data class Config(
    val orgFile: String,
    val orgEventsPath: String,
    val calendarId: String,
    val credentialFile: String = "./credentials.json",
    val createEventsMarkedAsDone: Boolean = false,
    private val todoKeywords: String = "TODO WAIT STRT PROJ",
    private val doneKeywords: String = "DONE KILL",
    val deleteGracePeriod: Int = 24
) {
    val stateKeywords
        get() = todoKeywords.split(" ") to doneKeywords.split(" ")

    companion object {
        fun load(fileName: String) = ConfigLoader().loadConfigOrThrow<Config>(File(fileName))
    }
}
