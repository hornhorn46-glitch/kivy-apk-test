package com.autodoctor.aipro.data.local

import com.autodoctor.aipro.core.model.DiagnosticTroubleCode
import com.autodoctor.aipro.core.model.DtcStatus
import com.autodoctor.aipro.core.model.ObdSession
import com.autodoctor.aipro.core.model.PidSample

fun PidSample.toEntity(sessionId: String): PidSampleEntity =
    PidSampleEntity(
        sessionId = sessionId,
        pid = pid,
        value = value,
        unit = unit,
        timestampMillis = timestampMillis,
    )

fun DiagnosticTroubleCode.toEntity(sessionId: String): DtcRecordEntity =
    DtcRecordEntity(
        sessionId = sessionId,
        code = code,
        description = description,
        status = status.name,
    )

fun List<PidSampleEntity>.toSamples(): List<PidSample> =
    map { entity ->
        PidSample(
            pid = entity.pid,
            value = entity.value,
            unit = entity.unit,
            timestampMillis = entity.timestampMillis,
        )
    }

fun List<DtcRecordEntity>.toDtcs(): List<DiagnosticTroubleCode> =
    map { entity ->
        DiagnosticTroubleCode(
            code = entity.code,
            description = entity.description,
            status = DtcStatus.valueOf(entity.status),
        )
    }

fun buildObdSession(
    session: ObdSessionEntity,
    samples: List<PidSampleEntity>,
    dtcs: List<DtcRecordEntity>,
): ObdSession =
    ObdSession(
        startedAtMillis = session.startedAtMillis,
        samples = samples.toSamples(),
        dtcs = dtcs.toDtcs(),
        freezeFrames = emptyList(),
    )
