package com.karmios.code.orggcalsync.utils

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default


class CommandLineArgs private constructor() : Args() {
    companion object {
        fun from(args: Array<out String>) = CommandLineArgs().also { it.parser.parse(args.asList().toTypedArray()) }
    }

    private val parser: ArgParser = ArgParser("org-gcal-sync")

    override val dry by parser.option(
        ArgType.Boolean,
        shortName="d",
        description="Skip sending event changes to Google"
    ).default(false)
    override val autoRetry by parser.option(
        ArgType.Boolean,
        shortName="r",
        description="Automatically retry on certain errors"
    ).default(false)
    override val configPath by parser.option(
        ArgType.String,
        fullName="config",
        shortName="c",
        description="Path to the desired config file"
    ).default(DEFAULT_CONFIG_FILE)
    override val logLevelRaw by parser.option(
        ArgType.Int,
        fullName="verbose",
        shortName="v",
        description="Logging verbosity"
    )
}