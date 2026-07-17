package com.autodoctor.aipro.core.performance

import com.autodoctor.aipro.core.model.ObdSession
import kotlin.math.roundToInt

data class EngineTestStep(
    val id: String,
    val title: String,
    val instruction: String,
    val minSpeedKph: Double,
    val maxSpeedKph: Double,
    val targetGear: Int?,
    val minThrottlePercent: Double,
    val minDurationSeconds: Double,
    val minRpm: Double,
    val maxRpm: Double,
)

data class EngineTestValidation(
    val valid: Boolean,
    val score: Double,
    val completedChecks: List<String>,
    val failedChecks: List<String>,
)

object EngineTestPlan {
    val safeSteps = listOf(
        EngineTestStep(
            id = "city-low-load",
            title = "Мягкий разгон 10-25 км/ч",
            instruction = "На свободном ровном участке разгонитесь до 10 км/ч. Включите 2 передачу или ручной режим 2. Нажмите газ примерно на 35-45% и плавно разгонитесь до 25 км/ч.",
            minSpeedKph = 10.0,
            maxSpeedKph = 25.0,
            targetGear = 2,
            minThrottlePercent = 32.0,
            minDurationSeconds = 4.0,
            minRpm = 1_200.0,
            maxRpm = 3_000.0,
        ),
        EngineTestStep(
            id = "mid-load",
            title = "Средняя нагрузка 25-55 км/ч",
            instruction = "На прямом безопасном участке держите 2 или 3 передачу. С 25 км/ч нажмите газ на 55-65% и разгонитесь до 55 км/ч без резких маневров.",
            minSpeedKph = 25.0,
            maxSpeedKph = 55.0,
            targetGear = null,
            minThrottlePercent = 52.0,
            minDurationSeconds = 5.0,
            minRpm = 1_500.0,
            maxRpm = 4_200.0,
        ),
        EngineTestStep(
            id = "controlled-wot",
            title = "Короткий WOT 40-80 км/ч",
            instruction = "Только если дорога свободна. На 3 передаче с 40 км/ч нажмите газ на 85-100% и отпустите после 80 км/ч или 5000 RPM.",
            minSpeedKph = 40.0,
            maxSpeedKph = 80.0,
            targetGear = 3,
            minThrottlePercent = 85.0,
            minDurationSeconds = 4.0,
            minRpm = 1_800.0,
            maxRpm = 5_000.0,
        ),
    )
}

class EngineTestValidator {
    fun validate(session: ObdSession, step: EngineTestStep): EngineTestValidation {
        val speed = session.valuesFor("SPEED")
        val throttle = session.valuesFor("THROTTLE")
        val rpm = session.valuesFor("RPM")
        val completed = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val duration = session.durationSeconds()

        fun check(condition: Boolean, ok: String, fail: String) {
            if (condition) completed += ok else failed += fail
        }

        check(duration >= step.minDurationSeconds, "Запись длилась ${duration.round1()} с", "Запись слишком короткая: ${duration.round1()} с")
        check(speed.any { it >= step.minSpeedKph }, "Стартовая скорость достигнута", "Не достигнута стартовая скорость ${step.minSpeedKph.roundToInt()} км/ч")
        check(speed.any { it >= step.maxSpeedKph }, "Финишная скорость достигнута", "Не достигнута финишная скорость ${step.maxSpeedKph.roundToInt()} км/ч")
        check(throttle.maxOrNull().orZero() >= step.minThrottlePercent, "Педаль газа нажата достаточно", "Газ не был нажат достаточно: нужно минимум ${step.minThrottlePercent.roundToInt()}%")
        check(rpm.any { it >= step.minRpm }, "RPM вошли в рабочий диапазон", "Обороты не дошли до ${step.minRpm.roundToInt()} RPM")
        check(rpm.any { it >= minOf(step.maxRpm, step.minRpm + 1_200.0) }, "RPM покрыли полезный диапазон", "Диапазон RPM слишком узкий для уверенного вывода")
        check(session.samples.size >= 25, "Достаточно точек live data", "Слишком мало точек OBD: ${session.samples.size}")

        val score = completed.size.toDouble() / (completed.size + failed.size).coerceAtLeast(1)
        return EngineTestValidation(
            valid = failed.isEmpty(),
            score = score,
            completedChecks = completed,
            failedChecks = failed,
        )
    }

    private fun ObdSession.valuesFor(pid: String): List<Double> =
        samples.filter { it.pid == pid }.map { it.value }

    private fun ObdSession.durationSeconds(): Double {
        if (samples.size < 2) return 0.0
        return (samples.maxOf { it.timestampMillis } - samples.minOf { it.timestampMillis }) / 1_000.0
    }

    private fun Double?.orZero(): Double = this ?: 0.0
    private fun Double.round1(): Double = (this * 10.0).roundToInt() / 10.0
}
