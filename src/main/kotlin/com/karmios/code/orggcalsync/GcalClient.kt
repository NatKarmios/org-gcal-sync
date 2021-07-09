package com.karmios.code.orggcalsync

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.DateTime
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.Events
import java.io.*
import java.time.LocalDate


class GcalClient (private val config: Config) {
    private val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    private val creds = getCredentials(this.httpTransport)
    private val service = getService()

    fun getEvents(startMonthOffset: Long = -2, endMonthOffset: Long = 6): List<Event> {
        val now = LocalDate.now()
        val rangeStart = now.plusMonths(startMonthOffset)
        val rangeEnd = now.plusMonths(endMonthOffset)
        val events: Events = service.events().list(config.calendarId)
            .setMaxResults(1000)
            .setTimeMin(DateTime(rangeStart.millis))
            .setTimeMax(DateTime(rangeEnd.millis))
            .setOrderBy("startTime")
            .setSingleEvents(true)
            .execute()
        return events.items
    }

    fun process(changes: Changes, dryRun: Boolean = false) {
        println("Create: ${changes.create.size}")
        println("Update: ${changes.update.size}")
        println("Delete: ${changes.delete.size}")
        val calls: List<(BatchRequest) -> Unit> =
            changes.create.map { { req: BatchRequest ->
                val r = service.events()
                    .insert(config.calendarId, it)
                r.queue(req, callback(it.summary, "create"))
            } } + changes.update.map { { req: BatchRequest ->
                val r = service.events()
                    .update(config.calendarId, it.first, it.second)
                r.queue(req, callback(it.second.summary, "update"))
                r.httpContent.writeTo(System.out)
                println()
            } } + changes.delete.map { { req: BatchRequest ->
                service.events()
                    .delete(config.calendarId, it)
                    .queue(req, callback(it, "delete"))
            } }

        calls.chunked(50).forEach { callChunk ->
            val req = service.batch()
            callChunk.forEach { it(req) }
            if (dryRun)
                println("Dry run - skipping Gcal execute!")
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
        val file = File(config.credentialFile)
        if (!file.exists()) throw FileNotFoundException("File not found: ${config.credentialFile}")
        val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, file.reader())

        // Build flow and trigger user authorization request.
        val flow = GoogleAuthorizationCodeFlow.Builder(
            HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES
        )
            .setDataStoreFactory(FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH)))
            .setAccessType("offline")
            .build()
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
