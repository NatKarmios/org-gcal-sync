package com.karmios.code.orggcalsync

import com.google.api.client.auth.oauth2.TokenResponseException
import com.karmios.code.orggcalsync.extern.GcalClient
import com.karmios.code.orggcalsync.org.Org
import com.karmios.code.orggcalsync.utils.*
import org.apache.logging.log4j.LogManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import kotlin.system.exitProcess


object OrgGcalSync {
    @JvmStatic
    fun main(vararg rawArgs: String) {
        val args = Args.from(rawArgs)
        val (success, _) = OrgGcalSync(args)
        if (!success)
            exitProcess(1)
    }

    operator fun invoke(args: Args, orgData: String? = null): Pair<Boolean, String> {
        var success = true
        val responses = mutableListOf<String>()

        val out = RedirectedPrintStream(System.out)
        val err = RedirectedPrintStream(System.err)
        System.setOut(out)
        System.setErr(err)

        val logLevelMsg = args.logLevel?.let { setLogLevel(it, out) } ?: ""
        val logger = LogManager.getLogger(OrgGcalSync::class.java.simpleName)
        if (logLevelMsg.isNotEmpty())
            logger.debug(logLevelMsg)

        try {
            val buf = ByteArrayOutputStream()
            PrintStream(buf, true, UTF_8).use {
                err.redirectTo(it)

                val config = logger.traceAction("loading config") { Config.load(args) }
                val org = logger.traceAction("loading org data") { Org.load(config, orgData) }
                val orgEvents = logger.traceAction("building events from org data") { org.findEvents() }
                val gcal = logger.traceAction("creating gcal client") { GcalClient(config) }
                val gcalEvents = logger.traceAction("loading gcal events") { gcal.getEvents() }
                val diff = logger.traceAction(
                    "finding differences between org and gcal events"
                ) { EventsDiff.from(orgEvents, gcalEvents, config) }
                val response = logger.traceAction("applying changes to gcal") { gcal.process(diff, args.dry) }

                responses += response
                err.reset()
            }
            val errs = buf.toString(UTF_8)
            if (errs.isNotEmpty()) {
                val errStr = "Encountered errors:\n${errs.indent()}"
                logger.debug(errStr)
                responses += errStr
            }
        } catch (e: TokenResponseException) {
            if (args.autoRetry && File("./tokens").deleteRecursively()) {
                logger.info("Google auth tokens are invalid/expired - deleting and restarting.")
                OrgGcalSync(args, orgData)
            } else {
                success = false
                val msg = "Google auth tokens are invalid/expired - can't continue!"
                logger.fatal(msg)
                responses.clear()
                responses += msg
            }
        } catch (e: Exception) {
            success = false
            val msg = e.message ?: "Failed with ${e.javaClass.name}!"
            logger.fatal(msg)
            responses.clear()
            responses += msg

            if (!logger.isDebugEnabled)
                logger.fatal("Run with higher verbosity for more info.")
            logger.debug(e.stackTraceToString())
        }

        return success to responses.joinToString("\n")
    }
}
