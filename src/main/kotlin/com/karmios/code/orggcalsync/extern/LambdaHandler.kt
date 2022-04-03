package com.karmios.code.orggcalsync.extern

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.karmios.code.orggcalsync.OrgGcalSync
import com.karmios.code.orggcalsync.utils.Args

@Suppress("unused")
class LambdaHandler : RequestHandler<LambdaHandler.In, LambdaHandler.Out> {
    class In {
        var orgData: String? = null
    }

    data class Out(val success: Boolean, val response: String)

    override fun handleRequest(input: In?, context: Context?): Out {
        val args = Args.from(emptyArray())
        val (success, response) = OrgGcalSync(args, input?.orgData)
        return Out(success, response)
    }
}
