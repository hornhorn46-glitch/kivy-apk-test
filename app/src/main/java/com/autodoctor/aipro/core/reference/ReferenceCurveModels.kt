package com.autodoctor.aipro.core.reference

import kotlinx.serialization.Serializable

@Serializable
data class ReferenceCurveSet(
    val id: String,
    val vehicleProfileId: String,
    val title: String,
    val source: String,
    val license: String,
    val curveKind: String,
    val limitations: List<String>,
    val curves: List<ReferenceCurve>,
)

@Serializable
data class ReferenceCurve(
    val metric: String,
    val xMetric: String,
    val xUnit: String,
    val yUnit: String,
    val points: List<ReferenceCurvePoint>,
)

@Serializable
data class ReferenceCurvePoint(
    val x: Double,
    val p10: Double,
    val p50: Double,
    val p90: Double,
    val sampleCount: Int,
)

data class CurveDeviation(
    val metric: String,
    val xMetric: String,
    val x: Double,
    val observed: Double,
    val expectedMedian: Double,
    val expectedLow: Double,
    val expectedHigh: Double,
    val severity: DeviationSeverity,
    val explanation: String,
)

enum class DeviationSeverity {
    Normal,
    Mild,
    Significant,
}

data class ReferenceComparisonReport(
    val curveSetId: String,
    val source: String,
    val deviations: List<CurveDeviation>,
    val limitations: List<String>,
)
