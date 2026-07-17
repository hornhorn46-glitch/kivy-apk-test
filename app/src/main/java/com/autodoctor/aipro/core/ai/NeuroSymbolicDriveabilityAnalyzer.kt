package com.autodoctor.aipro.core.ai

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

class NeuroSymbolicDriveabilityAnalyzer(
    private val model: DriveabilityModel,
) {
    fun analyze(facts: Map<String, Any>): DriveabilityAnalysis {
        val values = model.features.associateWith { feature ->
            rawFeatureValue(facts[feature.id]) ?: feature.missingValue
        }
        val missingFeatures = model.features
            .filter { rawFeatureValue(facts[it.id]) == null }
            .map { it.id }
        val normalized = values
            .map { (feature, value) -> feature.id to normalize(feature, value) }
            .toMap()
        val scores = model.outputs.associateWith { output ->
            output.bias + output.weights.entries.sumOf { (feature, weight) ->
                weight * (normalized[feature] ?: 0.0)
            }
        }
        val probabilities = softmax(scores)
        val observedFeatureCount = model.features.count { rawFeatureValue(facts[it.id]) != null }
        val observedFeatureRatio = (observedFeatureCount.toDouble() / max(model.features.size, 1)).coerceIn(0.0, 1.0)
        val coverage = rawFeatureValue(facts["diagnostic_data_coverage"]) ?: 0.0
        val dtcSignal = if ((rawFeatureValue(facts["dtc_count"]) ?: 0.0) > 0.0) 0.55 else 0.0
        val dataCompleteness = max(max(coverage, observedFeatureRatio), dtcSignal).coerceIn(0.0, 1.0)

        val predictions = model.outputs.map { output ->
            val probability = probabilities[output] ?: 0.0
            val signalCoverage = max(dataCompleteness, coverage).coerceIn(0.0, 1.0)
            val confidence = (probability * (0.35 + signalCoverage * 0.65)).coerceIn(0.0, 1.0)
            DriveabilityPrediction(
                rootCause = output.rootCause,
                title = output.title,
                probability = probability,
                confidence = confidence,
                physicalExplanation = output.physicalExplanation,
                recommendedChecks = output.recommendedChecks,
                evidence = evidence(output, values, normalized),
                sources = output.sources,
            )
        }.sortedByDescending { it.probability }

        return DriveabilityAnalysis(
            predictions = predictions,
            insufficientData = dataCompleteness < 0.45 && coverage < 0.45,
            dataCompleteness = dataCompleteness,
            missingFeatures = missingFeatures,
        )
    }

    private fun evidence(
        output: DriveabilityOutput,
        values: Map<DriveabilityFeature, Double>,
        normalized: Map<String, Double>,
    ): List<DriveabilityEvidence> {
        val featureById = model.features.associateBy { it.id }
        return output.weights.mapNotNull { (featureId, weight) ->
            val feature = featureById[featureId] ?: return@mapNotNull null
            val normalizedValue = normalized[featureId] ?: return@mapNotNull null
            val rawValue = values[feature] ?: return@mapNotNull null
            DriveabilityEvidence(
                feature = featureId,
                rawValue = rawValue,
                normalizedValue = normalizedValue,
                weight = weight,
                contribution = weight * normalizedValue,
            )
        }
            .sortedByDescending { abs(it.contribution) }
            .take(5)
    }

    private fun normalize(feature: DriveabilityFeature, value: Double): Double {
        val scale = if (abs(feature.scale) < 1e-9) 1.0 else feature.scale
        return ((value - feature.center) / scale).coerceIn(-6.0, 6.0)
    }

    private fun rawFeatureValue(value: Any?): Double? {
        return when (value) {
            is Number -> value.toDouble()
            is Boolean -> if (value) 1.0 else 0.0
            is String -> when (value.lowercase()) {
                "true", "yes", "1" -> 1.0
                "false", "no", "0" -> 0.0
                else -> value.toDoubleOrNull()
            }
            else -> null
        }
    }

    private fun softmax(scores: Map<DriveabilityOutput, Double>): Map<DriveabilityOutput, Double> {
        val maxScore = scores.values.maxOrNull() ?: 0.0
        val exponents = scores.mapValues { exp(it.value - maxScore) }
        val denominator = exponents.values.sum().coerceAtLeast(1e-9)
        return exponents.mapValues { it.value / denominator }
    }
}
