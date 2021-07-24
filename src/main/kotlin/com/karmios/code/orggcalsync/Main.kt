package com.karmios.code.orggcalsync

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext


fun setLogLevel(level: Level) {
    val ctx = LogManager.getContext(false) as LoggerContext
    val cfg = ctx.configuration
    val rootCfg = cfg.getLoggerConfig(LogManager.ROOT_LOGGER_NAME)
    rootCfg.level = level
}

fun main(vararg rawArgs: String) {
    val args = Args.from(rawArgs)
    setLogLevel(args.logLevel)
    val logger = LogManager.getLogger("main")

    try {
        logger.info("Loading config...")
        val config = Config.load(args.configPath)
        logger.info("Done loading config.")

        logger.info("Loading org data...")
        val org = Org.load(config)
        logger.info("Done loading org data.")

        logger.info("Building events from org data...")
        val orgEvents = org.findEvents()
        logger.info("Done building events from org data.")

        logger.info("Creating gcal client...")
        val gcal = GcalClient(config)
        logger.info("Done creating gcal client.")

        logger.info("Loading gcal events...")
        val gcalEvents = gcal.getEvents()
        logger.info("Done loading gcal events.")

        logger.info("Finding differences between org and gcal events...")
        val changes = Changes.from(orgEvents, gcalEvents, config)
        logger.info("Done finding differences between org and gcal events.")

        logger.info("Applying changes to gcal...")
        gcal.process(changes, args.dryRun)
        logger.info("Done applying changes to gcal...")
    } catch (e: Exception) {
        logger.fatal(e.message ?: "Failed with ${e.javaClass.name}!")
        logger.debug(e.stackTraceToString())
    }
}
