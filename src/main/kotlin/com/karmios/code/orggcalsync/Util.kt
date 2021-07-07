package com.karmios.code.orggcalsync

import com.orgzly.org.datetime.OrgDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.*


typealias EventDate = Pair<Calendar, Boolean>

val LocalDate.millis: Long
    get() = this.toEpochSecond(LocalTime.MIDNIGHT, ZoneOffset.UTC) * 1000

val OrgDateTime.asEventDate: EventDate
    get() = this.calendar to this.hasTime()
