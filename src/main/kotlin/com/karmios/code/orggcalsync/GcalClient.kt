package com.karmios.code.orggcalsync

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.auth.oauth2.TokenResponse
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpContent
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.DateTime
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.client.util.store.MemoryDataStoreFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.Events
import org.apache.logging.log4j.LogManager
import java.io.*
import java.time.LocalDate


/**
 * Google Calendar client
 *
 * @property config Configuration
 * @constructor
 */
class GcalClient (private val config: Config) {
    private val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    private val creds = getCredentials(this.httpTransport)
    private val service = getService()
    private val logger = LogManager.getLogger(GcalClient::class.java.simpleName)

    /**
     * Gets a list of events from Google Calendar
     *
     * @param startMonthOffset Lower bound (in months from now) for event times
     * @param endMonthOffset Upper bound (in months from now) for event times
     * @return The retrieved list of events
     */
    fun getEvents(startMonthOffset: Long = -1, endMonthOffset: Long = 6): List<Event> {
        val now = LocalDate.now()
        val rangeStart = now.plusMonths(startMonthOffset).plusDays(1)
        val rangeEnd = now.plusMonths(endMonthOffset)
        val events: Events = service.events().list(config.calendarId)
            .setMaxResults(1000)
            .setTimeMin(DateTime(rangeStart.toMillis(config.zoneOffset)))
            .setTimeMax(DateTime(rangeEnd.toMillis(config.zoneOffset)))
            .setOrderBy("startTime")
            .setSingleEvents(true)
            .execute()
        return events.items
            .also { logger.debug("Found Gcal events: " + it.joinToString(", ") { e -> e.summary }) }
    }

    /**
     * Sends a set of changes to Google Calendar
     *
     * @param changes The changes to send
     * @param dryRun Whether this is a dry run
     */
    fun process(changes: Changes, dryRun: Boolean = false) {
        val sizes = with(changes) { listOf(create, update, delete) }.map { it.size }
        if (sizes.all { it == 0 }) {
            logger.info("No changes to process!")
            return
        }
        logger.info("Processing ${sizes[0]} creation(s), ${sizes[1]} update(s), and ${sizes[2]} deletion(s)")
        val calls: List<(BatchRequest) -> String> =
            changes.create.map { event -> { req: BatchRequest ->
                service.events()
                    .insert(config.calendarId, event)
                    .also { it.queue(req, callback(event.summary, "create")) }
                    .httpContent.asString
            } } + changes.update.map { (id, event) -> { req: BatchRequest ->
                service.events()
                    .update(config.calendarId, id, event)
                    .also { it.queue(req, callback(event.summary, "update")) }
                    .httpContent.asString
            } } + changes.delete.map { (id, eventName) -> { req: BatchRequest ->
                service.events()
                    .delete(config.calendarId, id)
                    .also { it.queue(req, callback(id, "delete")) }
                "[Delete $eventName]"
            } }

        calls.chunked(50)
            .also { logger.debug("Sending ${it.size} batch requests") }
            .forEach { callChunk ->
                val req = service.batch()
                callChunk.forEach {
                    val reqContent = it(req)
                    logger.trace("Queueing change: $reqContent")
                }
                if (dryRun)
                    logger.warn("Dry run - skipping Gcal execute!")
                else
                    req.execute()
            }
    }

    // <editor-fold desc="Boilerplate">

    companion object {
        private const val APPLICATION_NAME = "Org Gcal Sync"
        private val JSON_FACTORY: JsonFactory = JacksonFactory.getDefaultInstance()
        private const val TOKENS_DIRECTORY_PATH = "tokens"
        private val SCOPES = listOf(CalendarScopes.CALENDAR)

        private val HttpContent.asString: String
            get() {
                val stream = ByteArrayOutputStream()
                this.writeTo(stream)
                return stream.toString()
            }

        private fun <T>callback(name: String, action: String): JsonBatchCallback<T> = object : JsonBatchCallback<T>() {
            override fun onSuccess(t: T?, responseHeaders: HttpHeaders?) {}

            override fun onFailure(e: GoogleJsonError?, responseHeaders: HttpHeaders?) {
                val err = e?.toPrettyString()?.let { ":\n$it\n"}
                println("\nFailed to $action event '$name'$err")
            }
        }
    }

    private fun getCredentials(HTTP_TRANSPORT: NetHttpTransport): Credential {
        // Load client secrets.
        val secretsReader = config.googleSecrets?.reader() ?: let {
            val secretsFile = File(config.credentialFile.expanded)
            if (!secretsFile.exists()) throw FileNotFoundException("File not found: ${config.credentialFile}")
            secretsFile.reader()
        }
        val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, secretsReader)

        // Build flow and trigger user authorization request.
        val flowBuilder = GoogleAuthorizationCodeFlow.Builder(
            HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES
        ).setAccessType("offline")

        val flow = if (config.googleRefreshToken == null) {
            flowBuilder.setDataStoreFactory(
                FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH))
            ).build()
        } else {
            val tokens = TokenResponse()
                .setRefreshToken(config.googleRefreshToken)
                .setAccessToken("")
                .setExpiresInSeconds(0)

            flowBuilder.setDataStoreFactory(MemoryDataStoreFactory())
                .build()
                .apply { createAndStoreCredential(tokens, "user") }
        }

        val receiver = LocalServerReceiver.Builder().setPort(8888).build()
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }

    private fun getService(): Calendar {
        return Calendar.Builder(httpTransport, JSON_FACTORY, creds)
            .setApplicationName(APPLICATION_NAME)
            .build()
    }

    // </editor-fold>
}
