package com.autodoctor.aipro.core.diagnostics

import com.autodoctor.aipro.core.model.ObdSession
import com.autodoctor.aipro.core.model.VehicleProfile
import com.autodoctor.aipro.core.model.latest
import com.autodoctor.aipro.core.model.values
import kotlin.math.max

class FactExtractor(
    private val enginePhysics: EnginePhysics = EnginePhysics(),
) {
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
        latest("BARO")?.let { facts["baro_kpa"] = it }
        latest("FUEL_LEVEL")?.let { facts["fuel_level_percent"] = it }
        latest("O2_B1S1")?.let { facts["o2_b1s1_v"] = it }
        latest("O2_B1S2")?.let { facts["o2_b1s2_v"] = it }
        latest("O2_B2S1")?.let { facts["o2_b2s1_v"] = it }
        latest("O2_B2S2")?.let { facts["o2_b2s2_v"] = it }
        avg("STFT_B1")?.let { facts["stft_b1_avg"] = it }
        avg("LTFT_B1")?.let { facts["ltft_b1_avg"] = it }
        avg("STFT_B2")?.let { facts["stft_b2_avg"] = it }
        avg("LTFT_B2")?.let { facts["ltft_b2_avg"] = it }
        avg("CONTROL_MODULE_VOLTAGE")?.let { facts["module_voltage_avg_v"] = it }
        avg("O2_B1S1")?.let { facts["o2_b1s1_avg_v"] = it }
        avg("O2_B1S2")?.let { facts["o2_b1s2_avg_v"] = it }
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
        val stftB2 = facts["stft_b2_avg"] as? Double
        val ltftB2 = facts["ltft_b2_avg"] as? Double
        if (stftB2 != null && ltftB2 != null) {
            facts["combined_trim_b2"] = stftB2 + ltftB2
        }
        val trimB1 = facts["combined_trim_b1"] as? Double
        val trimB2 = facts["combined_trim_b2"] as? Double
        if (trimB1 != null && trimB2 != null) {
            facts["combined_trim_max_abs"] = max(kotlin.math.abs(trimB1), kotlin.math.abs(trimB2))
            facts["bank_trim_split"] = kotlin.math.abs(trimB1 - trimB2)
            facts["global_combined_trim_avg"] = (trimB1 + trimB2) / 2.0
        } else {
            trimB1?.let {
                facts["combined_trim_max_abs"] = kotlin.math.abs(it)
                facts["global_combined_trim_avg"] = it
            }
        }

        val dtcCodes = session.dtcs.map { it.code }
        facts["dtc_codes"] = dtcCodes
        facts["dtc_count"] = dtcCodes.size
        facts["dtc_groups"] = dtcGroups(dtcCodes)
        facts["has_misfire_dtc"] = dtcCodes.any { it == "P0300" || Regex("P030[1-8]").matches(it) }
        facts["has_lean_dtc"] = dtcCodes.any { it == "P0171" || it == "P0174" }
        facts["has_rich_dtc"] = dtcCodes.any { it == "P0172" || it == "P0175" }
        facts["has_catalyst_dtc"] = dtcCodes.any { it == "P0420" || it == "P0430" }
        facts["has_throttle_dtc"] = dtcCodes.any { it in THROTTLE_CODES }
        facts["has_knock_dtc"] = dtcCodes.any { it in KNOCK_CODES }
        facts["has_voltage_dtc"] = dtcCodes.any { it in VOLTAGE_CODES }
        facts["has_fuel_pressure_dtc"] = dtcCodes.any { it in FUEL_PRESSURE_CODES }
        facts["has_o2_sensor_dtc"] = dtcCodes.any { it in O2_SENSOR_CODES }
        facts["has_evap_dtc"] = dtcCodes.any { it in EVAP_CODES }
        facts["has_egr_dtc"] = dtcCodes.any { it in EGR_CODES }
        facts["has_vvt_dtc"] = dtcCodes.any { it in VVT_CODES }
        facts["has_cam_crank_sync_dtc"] = dtcCodes.any { it in CAM_CRANK_CODES }
        facts["has_boost_dtc"] = dtcCodes.any { it in BOOST_CODES }
        facts["has_transmission_dtc"] = dtcCodes.any { it.startsWith("P07") || it in TRANSMISSION_CODES }
        facts["has_dpf_dtc"] = dtcCodes.any { it in DPF_CODES }
        facts["has_injector_dtc"] = dtcCodes.any { it in INJECTOR_CODES || Regex("P02[0-9A-F][0-9A-F]").matches(it) }

        val maf = facts["maf_gps"] as? Double
        val rpm = facts["rpm"] as? Double
        val displacement = profile.displacementLiters
        if (maf != null && rpm != null && displacement != null && rpm > 0.0) {
            val expectedAt85Ve = displacement * rpm * 0.85 / 120.0
            facts["maf_expected_85ve_gps"] = expectedAt85Ve
            facts["maf_ratio_to_expected"] = maf / max(expectedAt85Ve, 1.0)
        }

        facts.putAll(enginePhysics.derive(profile, session))

        return facts
    }

    private fun dtcGroups(codes: List<String>): List<String> {
        return buildList {
            if (codes.any { it == "P0300" || Regex("P030[1-8]").matches(it) }) add("misfire")
            if (codes.any { it == "P0171" || it == "P0174" }) add("lean")
            if (codes.any { it == "P0172" || it == "P0175" }) add("rich")
            if (codes.any { it in FUEL_PRESSURE_CODES }) add("fuel_pressure")
            if (codes.any { it in O2_SENSOR_CODES }) add("oxygen_sensor")
            if (codes.any { it == "P0420" || it == "P0430" }) add("catalyst")
            if (codes.any { it in EVAP_CODES }) add("evap")
            if (codes.any { it in EGR_CODES }) add("egr")
            if (codes.any { it in THROTTLE_CODES }) add("throttle")
            if (codes.any { it in KNOCK_CODES }) add("knock")
            if (codes.any { it in VOLTAGE_CODES }) add("voltage")
            if (codes.any { it in VVT_CODES }) add("vvt")
            if (codes.any { it in CAM_CRANK_CODES }) add("cam_crank_sync")
            if (codes.any { it in BOOST_CODES }) add("boost")
            if (codes.any { it.startsWith("P07") || it in TRANSMISSION_CODES }) add("transmission")
            if (codes.any { it in DPF_CODES }) add("dpf")
            if (codes.any { it in INJECTOR_CODES || Regex("P02[0-9A-F][0-9A-F]").matches(it) }) add("injector")
        }
    }

    private companion object {
        val THROTTLE_CODES = setOf("P0120", "P0121", "P0122", "P0123", "P0220", "P0221", "P0222", "P0223", "P2101", "P2119", "P2135")
        val KNOCK_CODES = setOf("P0325", "P0326", "P0327", "P0328", "P0330", "P0332", "P0333")
        val VOLTAGE_CODES = setOf("P0560", "P0562", "P0563")
        val FUEL_PRESSURE_CODES = setOf("P0087", "P0088", "P0090", "P0091", "P0092", "P0190", "P0191", "P0192", "P0193", "P0194")
        val O2_SENSOR_CODES = setOf("P0130", "P0131", "P0132", "P0133", "P0134", "P0135", "P0136", "P0137", "P0138", "P0139", "P0140", "P0141", "P0150", "P0151", "P0152", "P0153", "P0154", "P0155")
        val EVAP_CODES = setOf("P0440", "P0441", "P0442", "P0443", "P0446", "P0455", "P0456", "P0496")
        val EGR_CODES = setOf("P0400", "P0401", "P0402", "P0403", "P0404", "P0405", "P0406")
        val VVT_CODES = setOf("P0010", "P0011", "P0012", "P0013", "P0014", "P0015", "P0020", "P0021", "P0022", "P0023", "P0024", "P0025")
        val CAM_CRANK_CODES = setOf("P0016", "P0017", "P0018", "P0019", "P0335", "P0336", "P0340", "P0341", "P0342", "P0343", "P0344")
        val BOOST_CODES = setOf("P0234", "P0235", "P0236", "P0237", "P0238", "P0243", "P0244", "P0299", "P2263")
        val TRANSMISSION_CODES = setOf("P0700", "P0715", "P0720", "P0730", "P0731", "P0732", "P0733", "P0734", "P0740", "P0741", "P0750", "P0755", "P0760")
        val DPF_CODES = setOf("P2002", "P242F", "P2452", "P2453", "P2463", "P244A", "P244B")
        val INJECTOR_CODES = setOf("P0201", "P0202", "P0203", "P0204", "P0205", "P0206", "P0207", "P0208")
    }
}
