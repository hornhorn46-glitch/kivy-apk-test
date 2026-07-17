package com.autodoctor.aipro.core.ai

import kotlinx.serialization.Serializable

@Serializable
data class DriveabilityModel(
    val id: String,
    val version: String,
    val target: String,
    val createdBy: String,
    val validation: DriveabilityValidation,
    val features: List<DriveabilityFeature>,
    val outputs: List<DriveabilityOutput>,
)

@Serializable
data class DriveabilityValidation(
    val targetAccuracy: Double,
    val measuredTestAccuracy: Double,
    val measuredMacroF1: Double,
    val testGraphCount: Int,
    val testVehicleGraphCount: Int,
    val validationKind: String,
    val limitations: List<String>,
)

@Serializable
data class DriveabilityFeature(
    val id: String,
    val missingValue: Double = 0.0,
    val center: Double = 0.0,
    val scale: Double = 1.0,
    val description: String = "",
)

@Serializable
data class DriveabilityOutput(
    val rootCause: String,
    val title: String,
    val bias: Double,
    val weights: Map<String, Double>,
    val physicalExplanation: String,
    val recommendedChecks: List<String>,
    val sources: List<String>,
)

data class DriveabilityEvidence(
    val feature: String,
    val rawValue: Double,
    val normalizedValue: Double,
    val weight: Double,
    val contribution: Double,
)

data class DriveabilityPrediction(
    val rootCause: String,
    val title: String,
    val probability: Double,
    val confidence: Double,
    val physicalExplanation: String,
    val recommendedChecks: List<String>,
    val evidence: List<DriveabilityEvidence>,
    val sources: List<String>,
)

data class DriveabilityAnalysis(
    val predictions: List<DriveabilityPrediction>,
    val insufficientData: Boolean,
    val dataCompleteness: Double,
    val missingFeatures: List<String>,
)
