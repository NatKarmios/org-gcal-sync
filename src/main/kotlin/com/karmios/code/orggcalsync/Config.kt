package com.karmios.code.orggcalsync

import com.sksamuel.hoplite.ConfigLoader
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.*

data class Config(
    val orgFile: String = System.getenv("ORG_FILE")
        ?: throw IllegalArgumentException("No org file supplied!"),
    val localOrgFile: Boolean = false,
    val calendarId: String = System.getenv("CALENDAR_ID")
        ?: throw IllegalArgumentException("No calendar ID supplied!"),
    val googleRefreshToken: String? = System.getenv("GOOGLE_REFRESH_TOKEN")?.ifEmpty { null },

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
    val timeZone: String? = null
) {
    val zoneOffset: ZoneOffset by lazy {
        if (timeZone in TimeZone.getAvailableIDs()) {
            TimeZone.getTimeZone("Europe/London").toZoneId()
        } else {
            ZoneId.systemDefault()
        }.rules.getOffset(Instant.now())
    }

    companion object {
        fun load(fileName: String) = ConfigLoader().loadConfigOrThrow<Config>(File(fileName.expanded))
    }
}
