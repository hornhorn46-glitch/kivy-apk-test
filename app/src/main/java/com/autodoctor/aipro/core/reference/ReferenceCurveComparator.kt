package com.autodoctor.aipro.core.reference

import com.autodoctor.aipro.core.model.ObdSession
import com.autodoctor.aipro.core.model.PidSample
import kotlin.math.abs

class ReferenceCurveComparator {
    fun compare(session: ObdSession, reference: ReferenceCurveSet): ReferenceComparisonReport {
        val samplesByMetric = session.samples.groupBy { it.pid.toMetricName() }
        val rpmSamples = samplesByMetric["rpm"].orEmpty()
        val speedSamples = samplesByMetric["speed_kph"].orEmpty()
        val deviations = mutableListOf<CurveDeviation>()

        for (curve in reference.curves) {
            when (curve.xMetric) {
                "rpm" -> deviations += compareByRpm(curve, samplesByMetric[curve.metric].orEmpty(), rpmSamples)
                "idle" -> deviations += compareIdle(curve, samplesByMetric[curve.metric].orEmpty(), rpmSamples, speedSamples)
            }
        }

        return ReferenceComparisonReport(
            curveSetId = reference.id,
            source = reference.source,
            deviations = deviations.sortedWith(compareByDescending<CurveDeviation> { it.severity.ordinal }.thenBy { it.metric }),
            limitations = reference.limitations,
        )
    }

    private fun compareByRpm(
        curve: ReferenceCurve,
        metricSamples: List<PidSample>,
        rpmSamples: List<PidSample>,
    ): List<CurveDeviation> {
        if (metricSamples.isEmpty() || rpmSamples.isEmpty()) return emptyList()
        return metricSamples.mapNotNull { sample ->
            val rpm = rpmSamples.nearest(sample.timestampMillis, 750L)?.value ?: return@mapNotNull null
            val band = curve.interpolate(rpm) ?: return@mapNotNull null
            deviation(curve, rpm, sample.value, band)
        }.filter { it.severity != DeviationSeverity.Normal }
            .groupBy { it.metric to it.severity }
            .values
            .mapNotNull { group -> group.maxByOrNull { abs(it.observed - it.expectedMedian) } }
    }

    private fun compareIdle(
        curve: ReferenceCurve,
        metricSamples: List<PidSample>,
        rpmSamples: List<PidSample>,
        speedSamples: List<PidSample>,
    ): List<CurveDeviation> {
        if (metricSamples.isEmpty()) return emptyList()
        val idleValues = metricSamples.mapNotNull { sample ->
            val rpm = rpmSamples.nearest(sample.timestampMillis, 750L)?.value
            val speed = speedSamples.nearest(sample.timestampMillis, 750L)?.value ?: 0.0
            if (rpm != null && rpm in 500.0..1_100.0 && speed < 3.0) sample.value else null
        }
        if (idleValues.size < 5) return emptyList()
        val observed = idleValues.average()
        val band = curve.points.firstOrNull() ?: return emptyList()
        val deviation = deviation(curve, 0.0, observed, band)
        return if (deviation.severity == DeviationSeverity.Normal) emptyList() else listOf(deviation)
    }

    private fun deviation(
        curve: ReferenceCurve,
        x: Double,
        observed: Double,
        band: ReferenceCurvePoint,
    ): CurveDeviation {
        val width = (band.p90 - band.p10).coerceAtLeast(1.0)
        val margin = width * 0.35
        val severity = when {
            observed in band.p10..band.p90 -> DeviationSeverity.Normal
            observed in (band.p10 - margin)..(band.p90 + margin) -> DeviationSeverity.Mild
            else -> DeviationSeverity.Significant
        }
        val direction = if (observed > band.p90) "выше" else "ниже"
        return CurveDeviation(
            metric = curve.metric,
            xMetric = curve.xMetric,
            x = x,
            observed = observed,
            expectedMedian = band.p50,
            expectedLow = band.p10,
            expectedHigh = band.p90,
            severity = severity,
            explanation = "${curve.metric} $direction референсного диапазона ${band.p10}-${band.p90} ${curve.yUnit}; медиана источника ${band.p50} ${curve.yUnit}.",
        )
    }

    private fun ReferenceCurve.interpolate(x: Double): ReferenceCurvePoint? {
        if (points.isEmpty()) return null
        val sorted = points.sortedBy { it.x }
        if (x <= sorted.first().x) return sorted.first()
        if (x >= sorted.last().x) return sorted.last()
        val upper = sorted.firstOrNull { it.x >= x } ?: return sorted.last()
        val lower = sorted.getOrNull(sorted.indexOf(upper) - 1) ?: return upper
        val ratio = (x - lower.x) / (upper.x - lower.x).coerceAtLeast(1.0)
        fun lerp(a: Double, b: Double): Double = a + (b - a) * ratio
        return ReferenceCurvePoint(
            x = x,
            p10 = lerp(lower.p10, upper.p10),
            p50 = lerp(lower.p50, upper.p50),
            p90 = lerp(lower.p90, upper.p90),
            sampleCount = minOf(lower.sampleCount, upper.sampleCount),
        )
    }

    private fun List<PidSample>.nearest(timestampMillis: Long, maxDistanceMillis: Long): PidSample? {
        return minByOrNull { abs(it.timestampMillis - timestampMillis) }
            ?.takeIf { abs(it.timestampMillis - timestampMillis) <= maxDistanceMillis }
    }

    private fun String.toMetricName(): String {
        return when (uppercase()) {
            "RPM" -> "rpm"
            "SPEED" -> "speed_kph"
            "THROTTLE" -> "throttle_percent"
            "LOAD" -> "load_percent"
            "COOLANT_TEMP" -> "coolant_c"
            "LTFT_B1" -> "ltft_b1"
            "STFT_B1" -> "stft_b1"
            "MAP" -> "map_kpa"
            "INTAKE_TEMP" -> "iat_c"
            "TIMING_ADVANCE" -> "timing_deg"
            "CONTROL_MODULE_VOLTAGE" -> "module_voltage_v"
            else -> lowercase()
        }
    }
}
