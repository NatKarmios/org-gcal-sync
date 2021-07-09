package com.karmios.code.orggcalsync

import kotlin.system.exitProcess


fun main(vararg rawArgs: String) {
    val args = Args(rawArgs)
    val config = Config.load(args.configPath)
    val org = Org.load(config)
    val orgEvents = org.findEvents() ?: exitProcess(1)
    val gcal = GcalClient(config)
    val gcalEvents = gcal.getEvents()
    val changes = Changes.from(orgEvents, gcalEvents)
    gcal.process(changes, args.dryRun)
}
