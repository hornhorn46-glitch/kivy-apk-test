package com.autodoctor.aipro.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AutoDoctorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVehicleProfile(profile: VehicleProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ObdSessionEntity)

    @Insert
    suspend fun insertSamples(samples: List<PidSampleEntity>)

    @Insert
    suspend fun insertDtcRecords(records: List<DtcRecordEntity>)

    @Query("SELECT * FROM obd_sessions ORDER BY startedAtMillis DESC")
    fun observeSessions(): Flow<List<ObdSessionEntity>>

    @Query("SELECT * FROM pid_samples WHERE sessionId = :sessionId ORDER BY timestampMillis ASC")
    fun observeSamples(sessionId: String): Flow<List<PidSampleEntity>>

    @Query("SELECT * FROM dtc_records WHERE sessionId = :sessionId ORDER BY code ASC")
    suspend fun getDtcRecords(sessionId: String): List<DtcRecordEntity>
}
