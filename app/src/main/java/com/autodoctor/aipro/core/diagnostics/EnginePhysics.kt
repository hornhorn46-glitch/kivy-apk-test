package com.autodoctor.aipro.core.diagnostics

import com.autodoctor.aipro.core.model.InductionType
import com.autodoctor.aipro.core.model.ObdSession
import com.autodoctor.aipro.core.model.PidSample
import com.autodoctor.aipro.core.model.VehicleProfile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class EnginePhysics {
    fun derive(profile: VehicleProfile, session: ObdSession): Map<String, Any> {
        val facts = mutableMapOf<String, Any>()
        val samples = session.samples
        val rpm = samples.byPid("RPM")
        val speed = samples.byPid("SPEED")
        val maf = samples.byPid("MAF")
        val map = samples.byPid("MAP")
        val throttle = samples.byPid("THROTTLE")
        val load = samples.byPid("LOAD")
        val stft = samples.byPid("STFT_B1")
        val ltft = samples.byPid("LTFT_B1")
        val timing = samples.byPid("TIMING_ADVANCE")
        val voltage = samples.byPid("CONTROL_MODULE_VOLTAGE")

        facts["sample_count"] = samples.size
        if (samples.size >= 2) {
            facts["duration_sec"] = ((samples.maxOf { it.timestampMillis } - samples.minOf { it.timestampMillis }) / 1000.0).round2()
        }
        rpm.maxOfOrNull { it.value }?.let { facts["max_rpm"] = it }
        rpm.minOfOrNull { it.value }?.let { facts["min_rpm"] = it }
        speed.maxOfOrNull { it.value }?.let { facts["max_speed_kph"] = it }
        speed.minOfOrNull { it.value }?.let { facts["min_speed_kph"] = it }

        val maxThrottle = throttle.maxOfOrNull { it.value }
        val maxLoad = load.maxOfOrNull { it.value }
        val maxRpm = rpm.maxOfOrNull { it.value }
        val maxMaf = maf.maxOfOrNull { it.value }
        val maxMap = map.maxOfOrNull { it.value }
        val minTiming = timing.minOfOrNull { it.value }
        val minVoltage = voltage.minOfOrNull { it.value }
        val displacement = profile.displacementLiters

        if (maxThrottle != null && maxLoad != null) {
            facts["load_response_ratio"] = (maxLoad / max(maxThrottle, 1.0)).round3()
        }
        if (maxThrottle != null && maxRpm != null) {
            facts["has_wot_pull"] = maxThrottle >= 75.0 && maxRpm >= 2500.0
        }
        if (maxMap != null) {
            facts["estimated_boost_kpa"] = (maxMap - ATMOSPHERIC_KPA).round1()
            facts["boost_expected_for_profile"] = profile.induction == InductionType.Turbocharged
        }
        if (maxMaf != null) {
            facts["estimated_power_kw_from_maf"] = (maxMaf * AIR_MASS_TO_KW).round1()
        }
        if (maxMaf != null && maxRpm != null && displacement != null && maxRpm > 0.0) {
            val expectedPeakMaf = expectedMaf(displacement, maxRpm, if (profile.induction == InductionType.Turbocharged) 1.15 else 0.85)
            facts["peak_maf_expected_gps"] = expectedPeakMaf.round1()
            facts["peak_maf_ratio_to_expected"] = (maxMaf / max(expectedPeakMaf, 1.0)).round3()
            facts["estimated_peak_ve"] = (maxMaf * 120.0 / max(displacement * maxRpm, 1.0)).round3()
        }
        if (maxLoad != null && maxMap != null) {
            facts["map_load_coherence"] = (maxMap / max(maxLoad, 1.0)).round3()
        }
        if (minTiming != null && maxLoad != null && maxLoad > 60.0) {
            facts["timing_retard_under_load"] = minTiming < 6.0
        }
        if (minVoltage != null) {
            facts["low_voltage_seen"] = minVoltage < 12.6
        }

        trimByMode(stft, ltft, rpm, speed, throttle, load, idle = true)?.let {
            facts["combined_trim_idle_b1"] = it.round2()
        }
        trimByMode(stft, ltft, rpm, speed, throttle, load, idle = false)?.let {
            facts["combined_trim_load_b1"] = it.round2()
        }
        val idleTrim = facts["combined_trim_idle_b1"] as? Double
        val loadTrim = facts["combined_trim_load_b1"] as? Double
        if (idleTrim != null && loadTrim != null) {
            facts["trim_load_delta_b1"] = (loadTrim - idleTrim).round2()
            facts["trim_pattern"] = when {
                idleTrim > 12.0 && loadTrim < idleTrim - 6.0 -> "idle_lean_bias"
                loadTrim > idleTrim + 6.0 && loadTrim > 10.0 -> "load_lean_bias"
                idleTrim < -10.0 && loadTrim < -10.0 -> "global_rich_bias"
                else -> "mixed_or_normal"
            }
        }

        val coverage = listOfNotNull(
            rpm.isNotEmpty(),
            throttle.isNotEmpty(),
            load.isNotEmpty(),
            stft.isNotEmpty(),
            ltft.isNotEmpty(),
            map.isNotEmpty() || maf.isNotEmpty(),
            session.dtcs.isNotEmpty(),
            (facts["has_wot_pull"] as? Boolean) == true,
        ).count { it } / 8.0
        facts["diagnostic_data_coverage"] = coverage.round2()

        return facts
    }

    private fun expectedMaf(displacementLiters: Double, rpm: Double, volumetricEfficiency: Double): Double {
        return displacementLiters * rpm * volumetricEfficiency / 120.0
    }

    private fun trimByMode(
        stft: List<PidSample>,
        ltft: List<PidSample>,
        rpm: List<PidSample>,
        speed: List<PidSample>,
        throttle: List<PidSample>,
        load: List<PidSample>,
        idle: Boolean,
    ): Double? {
        val values = mutableListOf<Double>()
        for (shortTrim in stft) {
            val longTrim = ltft.nearest(shortTrim.timestampMillis, 800L)?.value ?: continue
            val rpmValue = rpm.nearest(shortTrim.timestampMillis, 800L)?.value
            val speedValue = speed.nearest(shortTrim.timestampMillis, 800L)?.value ?: 0.0
            val throttleValue = throttle.nearest(shortTrim.timestampMillis, 800L)?.value
            val loadValue = load.nearest(shortTrim.timestampMillis, 800L)?.value
            val matches = if (idle) {
                rpmValue != null && rpmValue in 550.0..1_150.0 && speedValue < 5.0
            } else {
                (throttleValue != null && throttleValue > 55.0) || (loadValue != null && loadValue > 55.0)
            }
            if (matches) values += shortTrim.value + longTrim
        }
        return values.takeIf { it.size >= 3 }?.average()
    }

    private fun List<PidSample>.byPid(pid: String): List<PidSample> =
        filter { it.pid == pid }.sortedBy { it.timestampMillis }

    private fun List<PidSample>.nearest(timestampMillis: Long, maxDistanceMillis: Long): PidSample? =
        minByOrNull { abs(it.timestampMillis - timestampMillis) }
            ?.takeIf { abs(it.timestampMillis - timestampMillis) <= maxDistanceMillis }

    private fun Double.round1(): Double = (this * 10.0).roundToInt() / 10.0
    private fun Double.round2(): Double = (this * 100.0).roundToInt() / 100.0
    private fun Double.round3(): Double = (this * 1000.0).roundToInt() / 1000.0

    private companion object {
        const val AIR_MASS_TO_KW = 1.22
        const val ATMOSPHERIC_KPA = 100.0
    }
}
