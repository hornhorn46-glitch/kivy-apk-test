package com.autodoctor.aipro.core.obd

class ElmResponseParser {
    fun parseService01Bytes(pid: String, response: ElmResponse): List<Int> {
        val normalizedPid = pid.uppercase()
        return response.lines
            .asSequence()
            .map { it.replace(" ", "").uppercase() }
            .filter { it.startsWith("41$normalizedPid") }
            .map { line ->
                line.drop(4)
                    .chunked(2)
                    .mapNotNull { byte -> byte.toIntOrNull(16) }
            }
            .firstOrNull()
            ?: emptyList()
    }

    fun parseDtcCodes(response: ElmResponse): List<String> {
        val bytes = response.lines
            .flatMap { it.replace(" ", "").chunked(2) }
            .mapNotNull { it.toIntOrNull(16) }
            .dropWhile { it != 0x43 }
            .drop(1)
        return bytes.chunked(2)
            .mapNotNull { pair ->
                if (pair.size < 2 || pair[0] == 0 && pair[1] == 0) return@mapNotNull null
                decodeDtc(pair[0], pair[1])
            }
    }

    private fun decodeDtc(first: Int, second: Int): String {
        val system = when (first shr 6 and 0x03) {
            0 -> "P"
            1 -> "C"
            2 -> "B"
            else -> "U"
        }
        val digit1 = first shr 4 and 0x03
        val digit2 = first and 0x0F
        val digit3 = second shr 4 and 0x0F
        val digit4 = second and 0x0F
        return "$system$digit1${digit2.toString(16).uppercase()}${digit3.toString(16).uppercase()}${digit4.toString(16).uppercase()}"
    }
}
