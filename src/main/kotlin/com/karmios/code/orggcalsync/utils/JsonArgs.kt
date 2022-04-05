package com.karmios.code.orggcalsync.utils

import com.beust.klaxon.JsonObject


class JsonArgs(json: JsonObject?) : Args() {
    override val dry = json?.boolean("dry") ?: false
    override val autoRetry = false
    override val configPath = DEFAULT_CONFIG_FILE
    override val logLevelRaw: Int? = null
}
