package com.autodoctor.aipro.core.knowledge

import kotlinx.serialization.Serializable

@Serializable
data class EncyclopediaArticle(
    val id: String,
    val title: String,
    val parameterIds: List<String>,
    val whatItIs: String,
    val normalValues: String,
    val deviations: List<String>,
    val influence: String,
    val checks: List<String>,
    val sources: List<String>,
)

@Serializable
data class EnginePhysicsPrinciple(
    val id: String,
    val title: String,
    val signals: List<String>,
    val formula: String,
    val graphPattern: String,
    val diagnosticUse: String,
    val recommendedChecks: List<String>,
    val sources: List<String>,
)

@Serializable
data class DiagnosticCasePattern(
    val id: String,
    val caseClass: String,
    val code: String,
    val title: String,
    val system: String,
    val vehicleScope: String,
    val symptoms: List<String>,
    val signalPattern: String,
    val graphFeatures: List<String>,
    val likelyRootCauses: List<String>,
    val recommendedChecks: List<String>,
    val postRepairVerification: String,
    val confidence: Double,
    val sources: List<String>,
)

@Serializable
data class DiagnosticCasePatternSummary(
    val id: String,
    val totalCases: Int,
    val counts: Map<String, Int>,
    val systems: Map<String, Int>,
    val generationPolicy: List<String>,
    val minimumRequestedCounts: Map<String, Int>,
)
