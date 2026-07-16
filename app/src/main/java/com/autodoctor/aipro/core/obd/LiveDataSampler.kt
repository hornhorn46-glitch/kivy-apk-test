package com.autodoctor.aipro.core.obd

import com.autodoctor.aipro.core.model.PidDefinition
import com.autodoctor.aipro.core.model.PidSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LiveDataSampler(
    private val connection: Elm327Connection,
    private val decoder: PidDecoder = PidDecoder(),
    private val parser: ElmResponseParser = ElmResponseParser(),
) {
    fun sample(
        pids: List<PidDefinition>,
        intervalMillis: Long = 120L,
    ): Flow<PidSample> = flow {
        val service01 = pids.filter { it.service == "01" }
        while (true) {
            for (pid in service01) {
                val response = connection.send(Elm327Commands.service01(pid.pid))
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
            }
            delay(intervalMillis)
        }
    }
}
