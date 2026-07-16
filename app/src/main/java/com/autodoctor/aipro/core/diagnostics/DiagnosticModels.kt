package com.autodoctor.aipro.core.diagnostics

import kotlinx.serialization.Serializable

@Serializable
data class DiagnosticRule(
    val id: String,
    val title: String,
    val category: String,
    val conditions: List<RuleCondition>,
    val probability: Double,
    val physicalExplanation: String,
    val symptoms: List<String>,
    val possibleCauses: List<String>,
    val recommendedChecks: List<String>,
    val repairRecommendations: List<String>,
    val confidence: Double,
    val severity: Severity,
    val sources: List<String>,
)

@Serializable
data class RuleCondition(
    val metric: String,
    val operator: ConditionOperator,
    val value: Double? = null,
    val values: List<String> = emptyList(),
    val weight: Double = 1.0,
    val required: Boolean = false,
)

@Serializable
enum class ConditionOperator {
    GreaterThan,
    LessThan,
    Between,
    Outside,
    Exists,
    ContainsAny,
}

@Serializable
enum class Severity {
    Info,
    Warning,
    Serious,
    Critical,
}

data class Evidence(
    val label: String,
    val value: String,
    val weight: Double,
)

data class DiagnosticHypothesis(
    val ruleId: String,
    val title: String,
    val probability: Double,
    val confidence: Double,
    val severity: Severity,
    val evidence: List<Evidence>,
    val missingData: List<String>,
    val contradictedBy: List<String>,
    val physicalExplanation: String,
    val symptoms: List<String>,
    val possibleCauses: List<String>,
    val recommendedChecks: List<String>,
    val repairRecommendations: List<String>,
    val sources: List<String>,
)

data class DiagnosticReport(
    val hypotheses: List<DiagnosticHypothesis>,
    val insufficientData: Boolean,
    val message: String,
)
