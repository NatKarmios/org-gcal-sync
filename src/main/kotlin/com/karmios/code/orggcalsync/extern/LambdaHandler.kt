package com.karmios.code.orggcalsync.extern

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.karmios.code.orggcalsync.OrgGcalSync
import com.karmios.code.orggcalsync.utils.Args
import com.lectra.koson.obj

@Suppress("unused")
class LambdaHandler : RequestHandler<String, String> {
    override fun handleRequest(orgData: String, context: Context): String {
        val args = Args.from(emptyArray())
        val (success, response) = OrgGcalSync(args, orgData)
        return obj {
            "success" to success
            "response" to response
        }.toString()
    }
}