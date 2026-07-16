package com.autodoctor.aipro.core.diagnostics

import com.autodoctor.aipro.core.model.ObdSession
import com.autodoctor.aipro.core.model.VehicleProfile
import kotlin.math.roundToInt

class InferenceEngine(
    private val rules: List<DiagnosticRule>,
    private val factExtractor: FactExtractor = FactExtractor(),
) {
    fun analyze(profile: VehicleProfile, session: ObdSession): DiagnosticReport {
        val facts = factExtractor.extract(profile, session)
        val hypotheses = rules.mapNotNull { evaluateRule(it, facts) }
            .sortedByDescending { it.probability * it.confidence }

        return if (hypotheses.isEmpty()) {
            DiagnosticReport(
                hypotheses = emptyList(),
                insufficientData = true,
                message = "Недостаточно данных для достоверного диагноза.",
            )
        } else {
            val top = hypotheses.first()
            DiagnosticReport(
                hypotheses = hypotheses,
                insufficientData = top.confidence < 0.45,
                message = if (top.confidence < 0.45) {
                    "Недостаточно данных для достоверного диагноза."
                } else {
                    "Найдены вероятные диагностические гипотезы."
                },
            )
        }
    }

    private fun evaluateRule(rule: DiagnosticRule, facts: Map<String, Any>): DiagnosticHypothesis? {
        val evidence = mutableListOf<Evidence>()
        val missing = mutableListOf<String>()
        val contradicted = mutableListOf<String>()
        var matchedWeight = 0.0
        var possibleWeight = 0.0
        var requiredFailed = false

        for (condition in rule.conditions) {
            possibleWeight += condition.weight
            val value = facts[condition.metric]
            if (value == null) {
                missing += condition.metric
                if (condition.required) requiredFailed = true
                continue
            }

            if (matches(condition, value)) {
                matchedWeight += condition.weight
                evidence += Evidence(condition.metric, value.toString(), condition.weight)
            } else {
                contradicted += "${condition.metric}=$value"
                if (condition.required) requiredFailed = true
            }
        }

        if (requiredFailed || matchedWeight <= 0.0) return null

        val matchRatio = matchedWeight / possibleWeight.coerceAtLeast(1.0)
        val contradictionPenalty = (contradicted.size * 0.08).coerceAtMost(0.35)
        val missingPenalty = (missing.size * 0.05).coerceAtMost(0.30)
        val probability = (rule.probability * (0.55 + matchRatio * 0.55) - contradictionPenalty)
            .coerceIn(0.0, 0.99)
        val confidence = (rule.confidence * matchRatio - missingPenalty - contradictionPenalty)
            .coerceIn(0.0, 0.99)

        if (probability < 0.25) return null

        return DiagnosticHypothesis(
            ruleId = rule.id,
            title = rule.title,
            probability = (probability * 100.0).roundToInt() / 100.0,
            confidence = (confidence * 100.0).roundToInt() / 100.0,
            severity = rule.severity,
            evidence = evidence,
            missingData = missing,
            contradictedBy = contradicted,
            physicalExplanation = rule.physicalExplanation,
            symptoms = rule.symptoms,
            possibleCauses = rule.possibleCauses,
            recommendedChecks = rule.recommendedChecks,
            repairRecommendations = rule.repairRecommendations,
            sources = rule.sources,
        )
    }

    private fun matches(condition: RuleCondition, fact: Any): Boolean {
        return when (condition.operator) {
            ConditionOperator.Exists -> true
            ConditionOperator.ContainsAny -> {
                val factValues = fact as? List<*> ?: return false
                condition.values.any { expected -> factValues.any { it == expected } }
            }
            ConditionOperator.GreaterThan ->
                (fact as? Number)?.toDouble()?.let { it > (condition.value ?: return false) } ?: false
            ConditionOperator.LessThan ->
                (fact as? Number)?.toDouble()?.let { it < (condition.value ?: return false) } ?: false
            ConditionOperator.Between -> {
                val values = condition.values.mapNotNull { it.toDoubleOrNull() }
                val value = (fact as? Number)?.toDouble() ?: return false
                values.size == 2 && value in values[0]..values[1]
            }
            ConditionOperator.Outside -> {
                val values = condition.values.mapNotNull { it.toDoubleOrNull() }
                val value = (fact as? Number)?.toDouble() ?: return false
                values.size == 2 && value !in values[0]..values[1]
            }
        }
    }
}
