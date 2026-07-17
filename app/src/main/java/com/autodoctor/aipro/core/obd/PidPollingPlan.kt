package com.autodoctor.aipro.core.obd

import com.autodoctor.aipro.core.model.PidDefinition

data class PidPollingPlan(
    val fast: List<PidDefinition>,
    val medium: List<PidDefinition>,
    val slow: List<PidDefinition>,
) {
    fun pidsForCycle(cycle: Long): List<PidDefinition> {
        return buildList {
            addAll(fast)
            if (cycle % 2L == 0L) addAll(medium)
            if (cycle % 5L == 0L) addAll(slow)
        }.distinctBy { it.id }
    }
}

object PidPollingPlanner {
    private val fastIds = setOf("RPM", "SPEED", "LOAD", "THROTTLE", "MAP", "MAF")
    private val mediumIds = setOf(
        "STFT_B1",
        "LTFT_B1",
        "STFT_B2",
        "LTFT_B2",
        "TIMING_ADVANCE",
        "LAMBDA",
        "O2_B1S1",
        "O2_B1S2",
        "CONTROL_MODULE_VOLTAGE",
        "FUEL_PRESSURE",
    )

    fun build(pids: List<PidDefinition>): PidPollingPlan {
        val service01 = pids.filter { it.service == "01" }
        val fast = service01.filter { it.id in fastIds }
        val medium = service01.filter { it.id in mediumIds && it.id !in fastIds }
        val slow = service01.filter { it.id !in fastIds && it.id !in mediumIds }
        return PidPollingPlan(
            fast = fast.ifEmpty { service01.take(6) },
            medium = medium,
            slow = slow,
        )
    }
}
