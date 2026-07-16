package com.autodoctor.aipro.core.diagnostics

import com.autodoctor.aipro.core.model.ObdSession
import com.autodoctor.aipro.core.model.VehicleProfile
import kotlin.math.max

class FactExtractor {
    fun extract(profile: VehicleProfile, session: ObdSession): Map<String, Any> {
        val facts = mutableMapOf<String, Any>()
        fun latest(pid: String): Double? = session.latest(pid)?.value
        fun avg(pid: String): Double? = session.values(pid).takeIf { it.isNotEmpty() }?.average()

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
        avg("STFT_B1")?.let { facts["stft_b1_avg"] = it }
        avg("LTFT_B1")?.let { facts["ltft_b1_avg"] = it }

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
}
