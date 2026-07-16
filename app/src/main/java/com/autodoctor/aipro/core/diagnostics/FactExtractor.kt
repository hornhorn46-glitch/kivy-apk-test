package com.autodoctor.aipro.core.diagnostics

import com.autodoctor.aipro.core.model.ObdSession
import com.autodoctor.aipro.core.model.VehicleProfile
import com.autodoctor.aipro.core.model.latest
import com.autodoctor.aipro.core.model.values
import kotlin.math.max

class FactExtractor {
    fun extract(profile: VehicleProfile, session: ObdSession): Map<String, Any> {
        val facts = mutableMapOf<String, Any>()
        fun latest(pid: String): Double? = session.latest(pid)?.value
        fun avg(pid: String): Double? = session.values(pid).takeIf { it.isNotEmpty() }?.average()
        fun maxValue(pid: String): Double? = session.values(pid).maxOrNull()
        fun minValue(pid: String): Double? = session.values(pid).minOrNull()

        latest("RPM")?.let { facts["rpm"] = it }
        latest("SPEED")?.let { facts["speed_kph"] = it }
        latest("MAF")?.let { facts["maf_gps"] = it }
        latest("MAP")?.let { facts["map_kpa"] = it }
        latest("THROTTLE")?.let { facts["throttle_percent"] = it }
        latest("LOAD")?.let { facts["load_percent"] = it }
        latest("COOLANT_TEMP")?.let { facts["coolant_c"] = it }
        latest("INTAKE_TEMP")?.let { facts["iat_c"] = it }
        latest("FUEL_PRESSURE")?.let { facts["fuel_pressure_kpa"] = it }
        latest("TIMING_ADVANCE")?.let { facts["timing_deg"] = it }
        latest("LAMBDA")?.let { facts["lambda"] = it }
        latest("CONTROL_MODULE_VOLTAGE")?.let { facts["module_voltage_v"] = it }
        avg("STFT_B1")?.let { facts["stft_b1_avg"] = it }
        avg("LTFT_B1")?.let { facts["ltft_b1_avg"] = it }
        avg("CONTROL_MODULE_VOLTAGE")?.let { facts["module_voltage_avg_v"] = it }
        maxValue("THROTTLE")?.let { facts["max_throttle_percent"] = it }
        maxValue("LOAD")?.let { facts["max_load_percent"] = it }
        maxValue("MAP")?.let { facts["max_map_kpa"] = it }
        maxValue("MAF")?.let { facts["max_maf_gps"] = it }
        minValue("TIMING_ADVANCE")?.let { facts["min_timing_deg"] = it }
        minValue("COOLANT_TEMP")?.let { facts["min_coolant_c"] = it }

        val stft = facts["stft_b1_avg"] as? Double
        val ltft = facts["ltft_b1_avg"] as? Double
        if (stft != null && ltft != null) {
            facts["combined_trim_b1"] = stft + ltft
        }

        val dtcCodes = session.dtcs.map { it.code }
        facts["dtc_codes"] = dtcCodes
        facts["has_misfire_dtc"] = dtcCodes.any { it == "P0300" || Regex("P030[1-8]").matches(it) }
        facts["has_lean_dtc"] = dtcCodes.any { it == "P0171" || it == "P0174" }
        facts["has_rich_dtc"] = dtcCodes.any { it == "P0172" || it == "P0175" }
        facts["has_catalyst_dtc"] = dtcCodes.any { it == "P0420" || it == "P0430" }
        facts["has_throttle_dtc"] = dtcCodes.any { it in THROTTLE_CODES }
        facts["has_knock_dtc"] = dtcCodes.any { it in KNOCK_CODES }
        facts["has_voltage_dtc"] = dtcCodes.any { it in VOLTAGE_CODES }

        val maf = facts["maf_gps"] as? Double
        val rpm = facts["rpm"] as? Double
        val displacement = profile.displacementLiters
        if (maf != null && rpm != null && displacement != null && rpm > 0.0) {
            val expectedAt85Ve = displacement * rpm * 0.85 / 120.0
            facts["maf_expected_85ve_gps"] = expectedAt85Ve
            facts["maf_ratio_to_expected"] = maf / max(expectedAt85Ve, 1.0)
        }

        return facts
    }

    private companion object {
        val THROTTLE_CODES = setOf("P0120", "P0121", "P0122", "P0123", "P0220", "P0221", "P0222", "P0223", "P2101", "P2119", "P2135")
        val KNOCK_CODES = setOf("P0325", "P0326", "P0327", "P0328", "P0330", "P0332", "P0333")
        val VOLTAGE_CODES = setOf("P0560", "P0562", "P0563")
    }
}
