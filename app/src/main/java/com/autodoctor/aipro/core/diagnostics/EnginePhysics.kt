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
        val coolant = samples.byPid("COOLANT_TEMP")
        val iat = samples.byPid("INTAKE_TEMP")
        val fuelLevel = samples.byPid("FUEL_LEVEL")
        val baro = samples.byPid("BARO")
        val o2B1S1 = samples.byPid("O2_B1S1")
        val o2B1S2 = samples.byPid("O2_B1S2")

        facts["sample_count"] = samples.size
        facts["unique_pid_count"] = samples.map { it.pid }.distinct().size
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
        val maxVoltage = voltage.maxOfOrNull { it.value }
        val maxIat = iat.maxOfOrNull { it.value }
        val maxCoolant = coolant.maxOfOrNull { it.value }
        val minCoolant = coolant.minOfOrNull { it.value }
        val minFuelLevel = fuelLevel.minOfOrNull { it.value }
        val latestBaro = baro.lastOrNull()?.value
        val displacement = profile.displacementLiters

        idleRpmStdDev(rpm, speed)?.let { idleStd ->
            facts["idle_rpm_stddev"] = idleStd.round2()
            facts["rough_idle_index"] = max(0.0, (idleStd - 55.0) / 145.0).round3()
        }
        if (rpm.size >= 2) {
            facts["rpm_delta"] = ((rpm.maxOf { it.value } - rpm.minOf { it.value }).coerceAtLeast(0.0)).round1()
        }
        if (speed.size >= 2) {
            facts["speed_delta_kph"] = ((speed.maxOf { it.value } - speed.minOf { it.value }).coerceAtLeast(0.0)).round1()
        }
        if (maxThrottle != null && maxLoad != null) {
            facts["load_response_ratio"] = (maxLoad / max(maxThrottle, 1.0)).round3()
            facts["throttle_load_mismatch"] = (maxThrottle - maxLoad).round2()
        }
        if (maxThrottle != null && maxRpm != null) {
            facts["has_wot_pull"] = maxThrottle >= 75.0 && maxRpm >= 2500.0
        }
        if (maxMap != null) {
            facts["estimated_boost_kpa"] = (maxMap - ATMOSPHERIC_KPA).round1()
            facts["boost_expected_for_profile"] = profile.induction == InductionType.Turbocharged
        }
        if (latestBaro != null && maxMap != null) {
            facts["map_minus_baro_peak_kpa"] = (maxMap - latestBaro).round1()
        }
        if (maxMaf != null) {
            facts["estimated_power_kw_from_maf"] = (maxMaf * AIR_MASS_TO_KW).round1()
        }
        if (maxMaf != null && maxRpm != null && displacement != null && maxRpm > 0.0) {
            val expectedPeakMaf = expectedMaf(displacement, maxRpm, if (profile.induction == InductionType.Turbocharged) 1.15 else 0.85)
            val peakMafRatio = maxMaf / max(expectedPeakMaf, 1.0)
            facts["peak_maf_expected_gps"] = expectedPeakMaf.round1()
            facts["peak_maf_ratio_to_expected"] = peakMafRatio.round3()
            facts["estimated_peak_ve"] = (maxMaf * 120.0 / max(displacement * maxRpm, 1.0)).round3()
            facts["wot_airflow_deficit"] = max(0.0, 1.0 - peakMafRatio).round3()
            facts["sensor_implausibility_index"] = max(0.0, abs(peakMafRatio - 0.95) - 0.28).round3()
            facts["specific_airflow_gps_per_liter"] = (maxMaf / max(displacement, 0.1)).round2()
            if (maxLoad != null && maxThrottle != null) {
                facts["clean_wot_adequacy_index"] =
                    if (peakMafRatio >= 0.80 && maxLoad >= 78.0 && maxThrottle >= 78.0) 1.0 else 0.0
            }
            profile.powerKw?.takeIf { it > 0.0 }?.let { ratedPower ->
                facts["power_ratio_to_reference"] = ((maxMaf * AIR_MASS_TO_KW) / ratedPower).round3()
            }
        }
        if (maxLoad != null && maxMap != null) {
            facts["map_load_coherence"] = (maxMap / max(maxLoad, 1.0)).round3()
        }
        idleMap(map, rpm, speed)?.let { idleMapKpa ->
            facts["idle_map_kpa"] = idleMapKpa.round2()
            facts["idle_vacuum_kpa"] = max(0.0, ATMOSPHERIC_KPA - idleMapKpa).round2()
        }
        if (minTiming != null && maxLoad != null && maxLoad > 60.0) {
            facts["timing_retard_under_load"] = minTiming < 6.0
            facts["spark_torque_loss_index"] = max(0.0, (12.0 - minTiming) / 12.0).round3()
        }
        if (minVoltage != null) {
            facts["low_voltage_seen"] = minVoltage < 12.6
            facts["low_voltage_index"] = max(0.0, (12.6 - minVoltage) / 2.0).round3()
        }
        if (maxVoltage != null) {
            facts["high_voltage_index"] = max(0.0, (maxVoltage - 15.1) / 1.5).round3()
        }
        if (maxCoolant != null) {
            facts["overheat_index"] = max(0.0, (maxCoolant - 108.0) / 18.0).round3()
        }
        if (minCoolant != null && maxCoolant != null) {
            facts["cold_operation_index"] = if (maxCoolant < 75.0) max(0.0, (75.0 - maxCoolant) / 30.0).round3() else 0.0
        }
        maxIat?.let {
            facts["thermal_air_density_loss_index"] = max(0.0, (it - 35.0) * 0.0032).round3()
            facts["iat_heat_soak_index"] = max(0.0, (it - 55.0) / 45.0).round3()
        }
        minFuelLevel?.let {
            facts["low_fuel_level_index"] = max(0.0, (12.0 - it) / 12.0).round3()
        }
        o2Stats("front_o2_b1", o2B1S1).forEach { (key, value) -> facts[key] = value }
        o2Stats("downstream_o2_b1", o2B1S2).forEach { (key, value) -> facts[key] = value }
        val frontRange = facts["front_o2_b1_range_v"] as? Double
        val rearRange = facts["downstream_o2_b1_range_v"] as? Double
        if (frontRange != null && rearRange != null) {
            facts["rear_o2_activity_ratio_b1"] = (rearRange / max(frontRange, 0.05)).round3()
            facts["catalyst_o2_similarity_index_b1"] = max(0.0, rearRange / max(frontRange, 0.05) - 0.55).round3()
        }
        if (maxThrottle != null && maxThrottle > 70.0) {
            val speedDelta = facts["speed_delta_kph"] as? Double ?: 0.0
            val rpmDelta = facts["rpm_delta"] as? Double ?: 0.0
            facts["wot_no_speed_gain_index"] = if (speedDelta < 8.0 && rpmDelta > 700.0) 1.0 else 0.0
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

        val airflowDeficit = facts["wot_airflow_deficit"] as? Double
        if (airflowDeficit != null && maxMap != null && maxThrottle != null && maxThrottle > 70.0) {
            val lowMapFactor = max(0.0, (86.0 - maxMap) / 28.0)
            val highMapFactor = max(0.0, (maxMap - 86.0) / 28.0)
            val timingLoss = facts["spark_torque_loss_index"] as? Double ?: 0.0
            facts["intake_restriction_index"] = (airflowDeficit * lowMapFactor).round3()
            facts["exhaust_restriction_index"] = (airflowDeficit * highMapFactor * max(0.35, timingLoss)).round3()
            val intakeRestriction = facts["intake_restriction_index"] as? Double ?: 0.0
            val exhaustRestriction = facts["exhaust_restriction_index"] as? Double ?: 0.0
            facts["mechanical_breathing_index"] =
                max(0.0, airflowDeficit - max(intakeRestriction, exhaustRestriction) - 0.18).round3()
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

    private fun idleMap(
        map: List<PidSample>,
        rpm: List<PidSample>,
        speed: List<PidSample>,
    ): Double? {
        val values = mutableListOf<Double>()
        for (mapSample in map) {
            val rpmValue = rpm.nearest(mapSample.timestampMillis, 800L)?.value ?: continue
            val speedValue = speed.nearest(mapSample.timestampMillis, 800L)?.value ?: 0.0
            if (rpmValue in 550.0..1_150.0 && speedValue < 5.0) {
                values += mapSample.value
            }
        }
        return values.takeIf { it.size >= 3 }?.average()
    }

    private fun idleRpmStdDev(
        rpm: List<PidSample>,
        speed: List<PidSample>,
    ): Double? {
        val values = rpm.mapNotNull { rpmSample ->
            val speedValue = speed.nearest(rpmSample.timestampMillis, 800L)?.value ?: 0.0
            rpmSample.value.takeIf { it in 450.0..1_300.0 && speedValue < 5.0 }
        }
        if (values.size < 5) return null
        val mean = values.average()
        return kotlin.math.sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    private fun o2Stats(prefix: String, samples: List<PidSample>): Map<String, Any> {
        if (samples.size < 5) return emptyMap()
        val values = samples.map { it.value }
        val min = values.min()
        val max = values.max()
        val switchCount = values.zipWithNext().count { (a, b) -> (a < 0.45 && b >= 0.45) || (a >= 0.45 && b < 0.45) }
        val range = max - min
        return mapOf(
            "${prefix}_min_v" to min.round3(),
            "${prefix}_max_v" to max.round3(),
            "${prefix}_range_v" to range.round3(),
            "${prefix}_switch_count" to switchCount.toDouble(),
            "${prefix}_stuck_index" to if (range < 0.12 && switchCount <= 1) 1.0 else 0.0,
        )
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
