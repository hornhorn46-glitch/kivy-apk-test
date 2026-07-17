package com.autodoctor.aipro.core.obd

class ElmResponseParser {
    fun parseService01Bytes(pid: String, response: ElmResponse): List<Int> {
        return parseServiceBytes(responseService = "41", pid = pid, response = response)
    }

    fun parseService02Bytes(pid: String, response: ElmResponse): List<Int> {
        return parseServiceBytes(responseService = "42", pid = pid, response = response)
    }

    fun parseServiceBytes(responseService: String, pid: String, response: ElmResponse): List<Int> {
        val normalizedPid = pid.uppercase()
        return response.lines
            .asSequence()
            .map { it.replace(" ", "").uppercase() }
            .filter { it.startsWith("$responseService$normalizedPid") }
            .map { line ->
                line.drop(4)
                    .chunked(2)
                    .mapNotNull { byte -> byte.toIntOrNull(16) }
            }
            .firstOrNull()
            ?: emptyList()
    }

    fun parseDtcCodes(response: ElmResponse, responseMode: Int = 0x43): List<String> {
        val bytes = response.lines
            .flatMap { it.replace(" ", "").chunked(2) }
            .mapNotNull { it.toIntOrNull(16) }
            .dropWhile { it != responseMode }
            .drop(1)
        return bytes.chunked(2)
            .mapNotNull { pair ->
                if (pair.size < 2 || pair[0] == 0 && pair[1] == 0) return@mapNotNull null
                decodeDtc(pair[0], pair[1])
            }
    }

    fun parseFreezeFrameTriggerDtc(response: ElmResponse): String? {
        val bytes = parseService02Bytes("02", response)
        val first = bytes.getOrNull(0) ?: return null
        val second = bytes.getOrNull(1) ?: return null
        if (first == 0 && second == 0) return null
        return decodeDtc(first, second)
    }

    fun parseSupportedService01Pids(response: ElmResponse, basePid: String): Set<String> {
        val base = basePid.toIntOrNull(16) ?: return emptySet()
        val bytes = parseService01Bytes(basePid, response).take(4)
        if (bytes.size < 4) return emptySet()
        return buildSet {
            for (bitIndex in 0 until 32) {
                val byte = bytes[bitIndex / 8]
                val mask = 1 shl (7 - (bitIndex % 8))
                if ((byte and mask) != 0) {
                    add((base + bitIndex + 1).toString(16).uppercase().padStart(2, '0'))
                }
            }
        }
    }

    fun parseMode06Monitors(response: ElmResponse): List<Mode06Monitor> {
        return response.lines
            .map { it.replace(" ", "").uppercase() }
            .filter { it.startsWith("46") && it.length >= 10 }
            .mapIndexed { index, line ->
                val bytes = line.chunked(2).mapNotNull { it.toIntOrNull(16) }
                Mode06Monitor(
                    id = "mode06-${index + 1}",
                    tid = bytes.getOrNull(1)?.toHexByte().orEmpty(),
                    cid = bytes.getOrNull(2)?.toHexByte(),
                    rawHex = line,
                    values = bytes.drop(1),
                )
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

    private fun Int.toHexByte(): String = toString(16).uppercase().padStart(2, '0')
}

data class Mode06Monitor(
    val id: String,
    val tid: String,
    val cid: String?,
    val rawHex: String,
    val values: List<Int>,
)
