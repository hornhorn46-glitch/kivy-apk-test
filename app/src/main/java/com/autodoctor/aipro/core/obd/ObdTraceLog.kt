package com.autodoctor.aipro.core.obd

import kotlinx.serialization.Serializable

@Serializable
data class ElmTraceEvent(
    val timestampMillis: Long,
    val transport: String,
    val command: String,
    val description: String,
    val rawResponse: String,
    val durationMillis: Long,
    val error: String? = null,
)

object ObdTraceLog {
    private const val MaxEvents = 120
    private val events = ArrayDeque<ElmTraceEvent>()

    @Synchronized
    fun record(event: ElmTraceEvent) {
        events += event
        while (events.size > MaxEvents) events.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<ElmTraceEvent> = events.toList()

    @Synchronized
    fun clear() {
        events.clear()
    }
}
