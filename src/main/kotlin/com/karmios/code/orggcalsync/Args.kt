package com.karmios.code.orggcalsync

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default

class Args(args: Array<out String>) {
    private val parser: ArgParser = ArgParser("org-gcal-sync")

    init {
        parser.parse(args.asList().toTypedArray())
    }

    val dryRun by parser.option(
        ArgType.Boolean,
        shortName="d",
        description="If present, skip sending event changes to Google"
    ).default(false)
    val configPath by parser.option(
        ArgType.String,
        fullName="config",
        shortName="c",
        description="Path to the desired config file"
    ).default("./config.yaml")
}