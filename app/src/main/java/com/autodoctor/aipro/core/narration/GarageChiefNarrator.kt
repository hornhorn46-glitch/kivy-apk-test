package com.autodoctor.aipro.core.narration

import com.autodoctor.aipro.core.diagnostics.DiagnosticHypothesis
import com.autodoctor.aipro.core.diagnostics.DiagnosticReport
import com.autodoctor.aipro.core.diagnostics.Severity
import kotlin.math.roundToInt

class GarageChiefNarrator {
    fun narrate(report: DiagnosticReport): GarageChiefVerdict {
        if (report.insufficientData || report.hypotheses.isEmpty()) {
            return GarageChiefVerdict(
                headline = "Машина пока молчит",
                feeling = "Данных мало. Нужен прогретый мотор, live data и короткий разгон под нагрузкой.",
                why = "Без RPM, throttle, load, trims, MAP/MAF и ошибок приложение не будет гадать.",
                nextMove = "Подключи ELM327, прогрей двигатель и запиши разгон. Тогда станет видно: мотор голодает, задыхается, троит или ЭБУ сам режет момент.",
                urgency = GarageUrgency.WaitForData,
            )
        }

        val top = report.hypotheses.first()
        return GarageChiefVerdict(
            headline = headline(top),
            feeling = feeling(top),
            why = why(top),
            nextMove = top.recommendedChecks.firstOrNull()
                ?: "Сначала повторить запись live data и подтвердить симптом на графиках.",
            urgency = urgency(top),
        )
    }

    fun preview(): GarageChiefVerdict {
        return GarageChiefVerdict(
            headline = "Я смотрю не на один датчик, а на всю картину",
            feeling = "Если машина не едет, причина обычно видна по связке: запрос газа, воздух, топливо, зажигание, пропуски, температура и питание.",
            why = "Высокий throttle без load говорит одно. Плюсовые trims под нагрузкой говорят другое. Позднее зажигание и низкий MAP сразу меняют приоритет проверок.",
            nextMove = "Лучший тест: прогретый двигатель, третья передача, разгон с 1500 до 5000 RPM, педаль в пол, запись всех доступных PID.",
            urgency = GarageUrgency.Ready,
        )
    }

    private fun headline(hypothesis: DiagnosticHypothesis): String {
        val probability = (hypothesis.probability * 100).roundToInt()
        return "${hypothesis.title}: $probability%"
    }

    private fun feeling(hypothesis: DiagnosticHypothesis): String {
        return when {
            hypothesis.ruleId.contains("fuel_delivery") -> "По ощущениям мотор просит топлива под нагрузкой, но система не успевает дать нужный поток."
            hypothesis.ruleId.contains("low_airflow") -> "Мотор как будто зажат: газ открыт, а воздух и нагрузка не растут как должны."
            hypothesis.ruleId.contains("restricted_exhaust") -> "Двигатель пытается дышать, но выпуск может держать его за горло."
            hypothesis.ruleId.contains("throttle") -> "Педаль может быть нажата, но дроссель или ЭБУ не дают мотору открыть момент."
            hypothesis.ruleId.contains("timing") -> "Тяга уходит, потому что зажигание откатывается поздно и давление в цилиндре приходит не вовремя."
            hypothesis.ruleId.contains("misfire") -> "Мотор не просто слабый: один или несколько цилиндров не делают свою работу стабильно."
            hypothesis.ruleId.contains("turbo") -> "Турбомотор не набирает расчетный воздух, поэтому момента нет."
            else -> "Главная гипотеза уже видна по нескольким признакам, но ее нужно подтвердить проверками."
        }
    }

    private fun why(hypothesis: DiagnosticHypothesis): String {
        val evidence = hypothesis.evidence.take(4).joinToString("; ") { "${it.label}=${it.value}" }
        return if (evidence.isBlank()) {
            hypothesis.physicalExplanation
        } else {
            "Зацепки: $evidence. ${hypothesis.physicalExplanation}"
        }
    }

    private fun urgency(hypothesis: DiagnosticHypothesis): GarageUrgency {
        return when (hypothesis.severity) {
            Severity.Critical -> GarageUrgency.StopAndCheck
            Severity.Serious -> GarageUrgency.DiagnoseSoon
            Severity.Warning -> GarageUrgency.WatchAndTest
            Severity.Info -> GarageUrgency.Ready
        }
    }
}

data class GarageChiefVerdict(
    val headline: String,
    val feeling: String,
    val why: String,
    val nextMove: String,
    val urgency: GarageUrgency,
)

enum class GarageUrgency {
    Ready,
    WaitForData,
    WatchAndTest,
    DiagnoseSoon,
    StopAndCheck,
}
