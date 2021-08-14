package com.karmios.code.orggcalsync

import com.google.api.client.auth.oauth2.TokenResponseException
import org.apache.logging.log4j.LogManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8

fun main(vararg rawArgs: String) {
    val out = RedirectedPrintStream(System.out)
    val err = RedirectedPrintStream(System.err)
    System.setOut(out)
    System.setErr(err)

    val args = Args.from(rawArgs)
    val logLevelMsg = setLogLevel(args.logLevel, out)
    val logger = LogManager.getLogger("Main")
    if (logLevelMsg.isNotEmpty())
        logger.debug(logLevelMsg)

    try {
        val buf = ByteArrayOutputStream()
        PrintStream(buf, true, UTF_8).use {
            err.redirectTo(it)

            val config = logger.traceAction("loading config") { Config.load(args.configPath) }
            val org = logger.traceAction("loading org data") { Org.load(config) }
            val orgEvents = logger.traceAction("building events from org data") { org.findEvents() }
            val gcal = logger.traceAction("creating gcal client") { GcalClient(config) }
            val gcalEvents = logger.traceAction("loading gcal events") { gcal.getEvents() }
            val changes = logger.traceAction(
                "finding differences between org and gcal events"
            ) { Changes.from(orgEvents, gcalEvents, config) }
            logger.traceAction("applying changes to gcal") { gcal.process(changes, args.dry) }

            err.reset()
        }
        val errs = buf.toString(UTF_8)
        if (errs.isNotEmpty()) {
            logger.debug("Encountered errors:\n${errs.indent()}")
        }
    } catch (e: TokenResponseException) {
        if (args.autoRetry && File("./tokens").deleteRecursively()) {
            logger.info("Google auth tokens are invalid/expired - deleting and restarting.")
            main(*rawArgs)
        } else {
            logger.fatal("Google auth tokens are invalid/expired - can't continue!")
        }
    } catch (e: Exception) {
        logger.fatal(e.message ?: "Failed with ${e.javaClass.name}!")
        if (!logger.isDebugEnabled)
            logger.fatal("Run with higher verbosity for more info.")
        logger.debug(e.stackTraceToString())
    }
}
