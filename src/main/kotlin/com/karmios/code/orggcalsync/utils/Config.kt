package com.karmios.code.orggcalsync.utils

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.yaml.YamlPropertySource
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

data class Config(
    val orgFile: String? = System.getenv("ORG_FILE").nullIfBlank,
    val localOrgFile: Boolean = !System.getenv("LOCAL_ORG_FILE").isNullOrBlank(),
    val calendarId: String = System.getenv("CALENDAR_ID").nullIfBlank
        ?: throw IllegalArgumentException("No calendar ID supplied!"),
    val googleRefreshToken: String? = System.getenv("GOOGLE_REFRESH_TOKEN").nullIfBlank,
    val googleSecrets: String? = System.getenv("GOOGLE_SECRETS").nullIfBlank,

    val orgEventsPath: String = "",
    val credentialFile: String = "./credentials.json",
    val todoKeywords: List<String> = listOf("TODO", "WAIT", "STRT", "PROJ"),
    val doneKeywords: List<String> = listOf("DONE", "KILL"),
    val flatten: Boolean = false,
    val includeTags: Set<String> = emptySet(),
    val includeOwnTags: Set<String> = emptySet(),
    val ignoreTags: Set<String> = emptySet(),
    val ignoreOwnTags: Set<String> = emptySet(),
    val ignoreTodos: Boolean = false,
    val createEventsMarkedAsDone: Boolean = false,
    val deleteGracePeriod: Int = 24,
    val timeZone: String? = null,
    val colorMap: Map<String, Int> = emptyMap(),
    val attendeeNicknames: Map<String, String> = emptyMap(),
) {
    val timeZoneId: ZoneId by lazy {
        timeZone?.toTimeZoneId()?.also { logger.debug("Found time zone '${it.id}'") }
            ?: let { ZoneId.systemDefault().also { logger.debug("Using system default time zone '${it.id}'") } }
    }

    val zoneOffset: ZoneOffset by lazy {
        timeZoneId.rules.getOffset(Instant.now())
    }

    companion object {
        val logger: Logger = LogManager.getLogger(Config::class.java.simpleName)

        fun load(args: Args): Config =
            ConfigLoader.Builder()
                .addPropertySource(YamlPropertySource(readResource("/config.yaml", args.configPath.expanded)))
                .build()
                .loadConfigOrThrow()
    }
}
