package com.autodoctor.aipro.core.obd

class PidDecoder {
    fun decodeService01(pid: String, bytes: List<Int>): Double? {
        return when (pid.uppercase()) {
            "04" -> bytes.getOrNull(0)?.let { it * 100.0 / 255.0 }
            "05" -> bytes.getOrNull(0)?.let { it - 40.0 }
            "06" -> bytes.getOrNull(0)?.let { it * 100.0 / 128.0 - 100.0 }
            "07" -> bytes.getOrNull(0)?.let { it * 100.0 / 128.0 - 100.0 }
            "08" -> bytes.getOrNull(0)?.let { it * 100.0 / 128.0 - 100.0 }
            "09" -> bytes.getOrNull(0)?.let { it * 100.0 / 128.0 - 100.0 }
            "0A" -> bytes.getOrNull(0)?.let { it * 3.0 }
            "0B" -> bytes.getOrNull(0)?.toDouble()
            "0C" -> two(bytes)?.let { ((it.first * 256) + it.second) / 4.0 }
            "0D" -> bytes.getOrNull(0)?.toDouble()
            "0E" -> bytes.getOrNull(0)?.let { it / 2.0 - 64.0 }
            "0F" -> bytes.getOrNull(0)?.let { it - 40.0 }
            "10" -> two(bytes)?.let { ((it.first * 256) + it.second) / 100.0 }
            "11" -> bytes.getOrNull(0)?.let { it * 100.0 / 255.0 }
            "14" -> bytes.getOrNull(0)?.let { it / 200.0 }
            "15" -> bytes.getOrNull(0)?.let { it / 200.0 }
            "16" -> bytes.getOrNull(0)?.let { it / 200.0 }
            "17" -> bytes.getOrNull(0)?.let { it / 200.0 }
            "2F" -> bytes.getOrNull(0)?.let { it * 100.0 / 255.0 }
            "33" -> bytes.getOrNull(0)?.toDouble()
            "42" -> two(bytes)?.let { ((it.first * 256) + it.second) / 1000.0 }
            "44" -> two(bytes)?.let { ((it.first * 256) + it.second) / 32768.0 }
            else -> null
        }
    }

    private fun two(bytes: List<Int>): Pair<Int, Int>? {
        val a = bytes.getOrNull(0) ?: return null
        val b = bytes.getOrNull(1) ?: return null
        return a to b
    }
}
