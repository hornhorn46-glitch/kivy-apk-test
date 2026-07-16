package com.autodoctor.aipro.core.export

import com.autodoctor.aipro.core.model.ObdSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SessionExporter(
    private val json: Json = Json { prettyPrint = true },
) {
    fun toJson(session: ObdSession): String = json.encodeToString(session)

    fun toCsv(session: ObdSession): String {
        val header = "timestampMillis,pid,value,unit"
        val rows = session.samples
            .sortedWith(compareBy({ it.timestampMillis }, { it.pid }))
            .joinToString("\n") { sample ->
                listOf(
                    sample.timestampMillis.toString(),
                    escape(sample.pid),
                    sample.value.toString(),
                    escape(sample.unit),
                ).joinToString(",")
            }
        return if (rows.isEmpty()) header else "$header\n$rows"
    }

    private fun escape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}
