package com.autodoctor.aipro.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PidDefinition(
    val id: String,
    val service: String,
    val pid: String,
    val name: String,
    val unit: String,
    val min: Double,
    val max: Double,
    val description: String = "",
    val formula: String = "",
)

@Serializable
data class PidSample(
    val pid: String,
    val value: Double,
    val unit: String,
    val timestampMillis: Long,
)

@Serializable
data class DiagnosticTroubleCode(
    val code: String,
    val description: String,
    val status: DtcStatus,
)

@Serializable
enum class DtcStatus {
    Confirmed,
    Pending,
    Permanent,
}

@Serializable
data class FreezeFrame(
    val triggerCode: String,
    val samples: List<PidSample>,
)

@Serializable
data class ObdSession(
    val startedAtMillis: Long,
    val samples: List<PidSample>,
    val dtcs: List<DiagnosticTroubleCode>,
    val freezeFrames: List<FreezeFrame>,
)

fun ObdSession.latest(pid: String): PidSample? =
    samples.asSequence().filter { it.pid == pid }.maxByOrNull { it.timestampMillis }

fun ObdSession.values(pid: String): List<Double> =
    samples.filter { it.pid == pid }.map { it.value }
