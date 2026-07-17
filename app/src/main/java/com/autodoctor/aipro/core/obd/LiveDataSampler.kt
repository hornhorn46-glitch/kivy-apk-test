package com.autodoctor.aipro.core.obd

import com.autodoctor.aipro.core.model.PidDefinition
import com.autodoctor.aipro.core.model.PidSample
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LiveDataSampler(
    private val connection: Elm327Connection,
    private val decoder: PidDecoder = PidDecoder(),
    private val parser: ElmResponseParser = ElmResponseParser(),
    private val session: RobustElm327Session = RobustElm327Session(
        connection = connection,
        timeoutMillis = 3_000L,
        retryCount = 1,
        retryDelayMillis = 80L,
    ),
) {
    fun sample(
        pids: List<PidDefinition>,
        intervalMillis: Long = 120L,
    ): Flow<PidSample> = flow {
        session.initialize()
        val plan = PidPollingPlanner.build(pids)
        val failureCounts = mutableMapOf<String, Int>()
        val disabledUntilCycle = mutableMapOf<String, Long>()
        var cycle = 0L
        while (true) {
            session.keepAliveIfNeeded()
            for (pid in plan.pidsForCycle(cycle)) {
                if ((disabledUntilCycle[pid.id] ?: 0L) > cycle) continue
                try {
                    val response = session.sendWithRetry(Elm327Commands.service01(pid.pid))
                    val bytes = parser.parseService01Bytes(pid.pid, response)
                    val value = decoder.decodeService01(pid.pid, bytes)
                    if (value != null) {
                        failureCounts.remove(pid.id)
                        disabledUntilCycle.remove(pid.id)
                        emit(
                            PidSample(
                                pid = pid.id,
                                value = value,
                                unit = pid.unit,
                                timestampMillis = System.currentTimeMillis(),
                            ),
                        )
                    } else {
                        val failures = failureCounts.getOrDefault(pid.id, 0) + 1
                        failureCounts[pid.id] = failures
                        if (failures >= 3) {
                            disabledUntilCycle[pid.id] = cycle + if (pid.id in criticalPidIds) 8L else 20L
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    val failures = failureCounts.getOrDefault(pid.id, 0) + 1
                    failureCounts[pid.id] = failures
                    if (failures >= if (pid.id in criticalPidIds) 2 else 1) {
                        disabledUntilCycle[pid.id] = cycle + if (pid.id in criticalPidIds) 8L else 20L
                    }
                }
            }
            cycle += 1
            delay(intervalMillis)
        }
    }

    private companion object {
        val criticalPidIds = setOf("RPM", "SPEED", "THROTTLE", "LOAD")
    }
}
