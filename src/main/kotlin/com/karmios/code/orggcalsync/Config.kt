package com.karmios.code.orggcalsync

import com.sksamuel.hoplite.ConfigLoader
import java.io.File

data class Config(
    val orgFile: String,
    val orgEventsPath: String,
    val calendarId: String,
    val credentialFile: String = "./credentials.json"
) {
    companion object {
        fun load(fileName: String) = ConfigLoader().loadConfigOrThrow<Config>(File(fileName))
    }
}
