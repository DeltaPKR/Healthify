package com.healthify.app.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The single answer to "which day is it?" for logs, check-ins and steps.
 * A day is the local calendar date (midnight rollover). Moving the
 * rollover (e.g. to 04:00 for night owls) should only need changes here.
 */
object DayClock {
    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun today(): LocalDate = LocalDate.now()
    fun todayIso(): String = iso(today())
    fun iso(date: LocalDate): String = date.format(ISO)

    /** Emits today's ISO date now and again whenever the day rolls over. */
    fun todayIsoFlow(): Flow<String> = flow {
        while (true) {
            emit(todayIso())
            delay(30_000L)
        }
    }.distinctUntilChanged()
}
