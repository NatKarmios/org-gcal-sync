package com.karmios.code.orggcalsync.extern

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent as Request
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent as Response
import com.beust.klaxon.Klaxon
import com.beust.klaxon.KlaxonException
import com.karmios.code.orggcalsync.OrgGcalSync
import com.karmios.code.orggcalsync.utils.Args
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger


@Suppress("unused")
class LambdaHandler : RequestHandler<Request, Response> {
    override fun handleRequest(event: Request, context: Context?): Response {
        val args = Args.from(emptyArray())
        val input = try {
            Klaxon().parseJsonObject((event.body ?: "").reader())
        } catch (_: KlaxonException) {
            logger.error("Failed to parse json:\n${event.body}")
            null
        }

        val orgData = input?.string("orgData")
        val (success, response) = OrgGcalSync(args, orgData)
        val responseBody = Klaxon().toJsonString(mapOf(
            "success" to success,
            "msg" to response
        ))

        return Response()
            .withStatusCode(200)
            .withBody(responseBody)
    }

    companion object {
        val logger: Logger = LogManager.getLogger(LambdaHandler::class.java.simpleName)
    }
}
