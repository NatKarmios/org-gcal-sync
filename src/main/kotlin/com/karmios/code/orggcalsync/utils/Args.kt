package com.karmios.code.orggcalsync.utils

import org.apache.logging.log4j.Level

abstract class Args {
    abstract val dry: Boolean
    abstract val autoRetry: Boolean
    abstract val configPath: String
    protected abstract val logLevelRaw: Int?

    val logLevel: Level?
        get() = logLevelRaw?.let { it.clamp(0, Level.values().size) * 100 }
            ?.let { Level.values().sortedBy { it.intLevel() }.find { l -> l.intLevel() >= it }!! }

    companion object {
        const val DEFAULT_CONFIG_FILE = "config.yaml"
    }
}