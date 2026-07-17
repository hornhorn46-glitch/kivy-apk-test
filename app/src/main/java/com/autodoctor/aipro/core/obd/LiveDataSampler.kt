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
    private val session: RobustElm327Session = RobustElm327Session(connection),
) {
    fun sample(
        pids: List<PidDefinition>,
        intervalMillis: Long = 120L,
    ): Flow<PidSample> = flow {
        session.initialize()
        val plan = PidPollingPlanner.build(pids)
        var cycle = 0L
        while (true) {
            session.keepAliveIfNeeded()
            for (pid in plan.pidsForCycle(cycle)) {
                try {
                    val response = session.sendWithRetry(Elm327Commands.service01(pid.pid))
                    val bytes = parser.parseService01Bytes(pid.pid, response)
                    val value = decoder.decodeService01(pid.pid, bytes)
                    if (value != null) {
                        emit(
                            PidSample(
                                pid = pid.id,
                                value = value,
                                unit = pid.unit,
                                timestampMillis = System.currentTimeMillis(),
                            ),
                        )
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    // Unsupported or temporarily unavailable PID should not stop the whole live stream.
                }
            }
            cycle += 1
            delay(intervalMillis)
        }
    }
}
