package com.autodoctor.aipro.core.performance

import com.autodoctor.aipro.core.model.ObdSession
import com.autodoctor.aipro.core.model.VehicleProfile
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

class AccelerationAnalyzer {
    fun estimatePower(
        profile: VehicleProfile,
        session: ObdSession,
        config: AccelerationTestConfig = AccelerationTestConfig(),
    ): CombinedPowerEstimate {
        val warnings = mutableListOf<String>()
        val estimates = mutableListOf<PowerEstimate>()
        val rpmSamples = session.samples.filter { it.pid == "RPM" }.sortedBy { it.timestampMillis }
        val speedSamples = session.samples.filter { it.pid == "SPEED" }.sortedBy { it.timestampMillis }
        val mafSamples = session.samples.filter { it.pid == "MAF" }.sortedBy { it.timestampMillis }
        val loadSamples = session.samples.filter { it.pid == "LOAD" }.sortedBy { it.timestampMillis }
        val throttleSamples = session.samples.filter { it.pid == "THROTTLE" }.sortedBy { it.timestampMillis }

        if (rpmSamples.none { it.value >= config.startRpm } || rpmSamples.none { it.value >= config.endRpm }) {
            warnings += "Тест разгона не покрывает требуемый диапазон оборотов ${config.startRpm.roundToInt()}-${config.endRpm.roundToInt()} RPM."
        }
        val maxThrottle = throttleSamples.maxOfOrNull { it.value }
        if (maxThrottle != null && maxThrottle < config.requireWideOpenThrottlePercent) {
            warnings += "Дроссель не был открыт достаточно широко; оценка мощности будет занижена."
        }

        estimates += estimateByAirMass(mafSamples)
        estimateByAcceleration(profile, speedSamples)?.let { estimates += it }
        estimateByLoad(profile, loadSamples)?.let { estimates += it }

        val valid = estimates.filter { it.confidence > 0.0 && it.powerKw > 0.0 }
        if (valid.isEmpty()) {
            return CombinedPowerEstimate(
                meanKw = 0.0,
                lowKw = 0.0,
                highKw = 0.0,
                confidence = 0.0,
                estimates = emptyList(),
                warnings = warnings + "Недостаточно данных для оценки мощности.",
            )
        }

        val weight = valid.sumOf { it.confidence }
        val mean = valid.sumOf { it.powerKw * it.confidence } / weight
        val spread = valid.sumOf { (it.powerKw - mean).pow(2) * it.confidence } / weight
        val sigma = max(spread.pow(0.5), mean * 0.06)
        val confidence = (valid.map { it.confidence }.average() - warnings.size * 0.08).coerceIn(0.0, 0.95)

        return CombinedPowerEstimate(
            meanKw = mean.roundOne(),
            lowKw = max(mean - sigma * 1.35, 0.0).roundOne(),
            highKw = (mean + sigma * 1.35).roundOne(),
            confidence = confidence.roundTwo(),
            estimates = valid,
            warnings = warnings,
        )
    }

    private fun estimateByAirMass(mafSamples: List<com.autodoctor.aipro.core.model.PidSample>): PowerEstimate {
        val peakMaf = mafSamples.maxOfOrNull { it.value } ?: 0.0
        val kw = peakMaf * AIR_MASS_TO_KW
        val confidence = when {
            peakMaf <= 0.0 -> 0.0
            peakMaf < 20.0 -> 0.35
            else -> 0.72
        }
        return PowerEstimate(
            method = PowerMethod.AirMass,
            powerKw = kw.roundOne(),
            confidence = confidence,
            explanation = "Оценка по пиковому MAF: больше воздуха при корректной смеси означает больший потенциальный тепловой поток и мощность.",
        )
    }

    private fun estimateByAcceleration(
        profile: VehicleProfile,
        speedSamples: List<com.autodoctor.aipro.core.model.PidSample>,
    ): PowerEstimate? {
        val mass = profile.massKg ?: return null
        if (speedSamples.size < 3) return null
        val first = speedSamples.first()
        val last = speedSamples.last()
        val dt = (last.timestampMillis - first.timestampMillis) / 1_000.0
        if (dt <= 0.5 || last.value <= first.value) return null
        val v0 = first.value / 3.6
        val v1 = last.value / 3.6
        val kineticPowerKw = 0.5 * mass * (v1 * v1 - v0 * v0) / dt / 1_000.0
        val aeroKw = AERO_DRAG_COEFF * v1.pow(3) / 1_000.0
        val rollingKw = mass * 9.81 * ROLLING_RESISTANCE * ((v0 + v1) / 2.0) / 1_000.0
        return PowerEstimate(
            method = PowerMethod.VehicleAcceleration,
            powerKw = (kineticPowerKw + aeroKw + rollingKw).roundOne(),
            confidence = 0.64,
            explanation = "Оценка по приросту кинетической энергии автомобиля с поправкой на аэродинамику и сопротивление качению.",
        )
    }

    private fun estimateByLoad(
        profile: VehicleProfile,
        loadSamples: List<com.autodoctor.aipro.core.model.PidSample>,
    ): PowerEstimate? {
        val referencePower = profile.powerKw ?: return null
        val peakLoad = loadSamples.maxOfOrNull { it.value } ?: return null
        return PowerEstimate(
            method = PowerMethod.CalculatedLoad,
            powerKw = (referencePower * (peakLoad / 100.0).coerceIn(0.0, 1.2)).roundOne(),
            confidence = 0.48,
            explanation = "Оценка по расчетной нагрузке ЭБУ относительно паспортной мощности профиля.",
        )
    }

    private fun Double.roundOne(): Double = (this * 10.0).roundToInt() / 10.0
    private fun Double.roundTwo(): Double = (this * 100.0).roundToInt() / 100.0

    private companion object {
        const val AIR_MASS_TO_KW = 1.22
        const val AERO_DRAG_COEFF = 0.42
        const val ROLLING_RESISTANCE = 0.014
    }
}
