package com.karmios.code.orggcalsync

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import org.apache.logging.log4j.Level


class Args private constructor() {
    companion object {
        const val DEFAULT_CONFIG_FILE = "./config.yaml"
        val DEFAULT_LOG_LEVEL: Level = Level.INFO

        fun from(args: Array<out String>) = Args().also { it.parser.parse(args.asList().toTypedArray()) }
    }

    private val parser: ArgParser = ArgParser("org-gcal-sync")
    val logLevel: Level
        get() = logLevelRaw?.let { it.clamp(0, Level.values().size) * 100 }
            ?.let { Level.values().sortedBy { it.intLevel() }.find { l -> l.intLevel() >= it }!! }
            ?: DEFAULT_LOG_LEVEL

    val dry by parser.option(
        ArgType.Boolean,
        shortName="d",
        description="Skip sending event changes to Google"
    ).default(false)
    val configPath by parser.option(
        ArgType.String,
        fullName="config",
        shortName="c",
        description="Path to the desired config file"
    ).default(DEFAULT_CONFIG_FILE)
    private val logLevelRaw by parser.option(
        ArgType.Int,
        fullName="verbose",
        shortName="v",
        description="Logging verbosity"
    )
}