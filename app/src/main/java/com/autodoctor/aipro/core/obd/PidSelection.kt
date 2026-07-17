package com.autodoctor.aipro.core.obd

import com.autodoctor.aipro.core.model.PidDefinition

object PidSelection {
    private val engineTestPriority = listOf(
        "RPM",
        "SPEED",
        "THROTTLE",
        "LOAD",
        "MAP",
        "MAF",
        "STFT_B1",
        "LTFT_B1",
        "STFT_B2",
        "LTFT_B2",
        "TIMING_ADVANCE",
        "CONTROL_MODULE_VOLTAGE",
        "COOLANT_TEMP",
        "INTAKE_TEMP",
        "LAMBDA",
        "O2_B1S1",
        "O2_B1S2",
        "O2_B2S1",
        "O2_B2S2",
        "FUEL_PRESSURE",
        "BARO",
        "FUEL_LEVEL",
    )

    fun engineTest(pids: List<PidDefinition>): List<PidDefinition> {
        val byId = pids.filter { it.service == "01" }.associateBy { it.id }
        return engineTestPriority.mapNotNull(byId::get)
    }
}
