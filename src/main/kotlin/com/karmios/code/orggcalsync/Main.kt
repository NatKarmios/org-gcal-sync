package com.karmios.code.orggcalsync

import kotlin.system.exitProcess


fun main(vararg args: String) {
    val (FILE_NAME, ORG_TREE_PATH, CALENDAR_ID) = getArgs(args)
    val org = Org.loadFrom(FILE_NAME)
    val orgEvents = org.findEventsAt(ORG_TREE_PATH) ?: exitProcess(1)
    val gcal = GcalClient(CALENDAR_ID)
    val gcalEvents = gcal.getEvents()
    val changes = Changes.from(orgEvents, gcalEvents)
    gcal.process(changes)
}

fun getArgs(args: Array<out String>): Triple<String, String, String> =
    try {
        Triple(args[0], args[1], args[2])
    } catch (e: ArrayIndexOutOfBoundsException) {
        println("Too few arguments")
        exitProcess(1)
    }