package com.autodoctor.aipro.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "vehicle_profiles")
data class VehicleProfileEntity(
    @PrimaryKey val id: String,
    val make: String,
    val model: String,
    val year: Int?,
    val engineCode: String?,
    val rawJson: String,
)

@Entity(
    tableName = "obd_sessions",
    foreignKeys = [
        ForeignKey(
            entity = VehicleProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleProfileId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("vehicleProfileId"), Index("startedAtMillis")],
)
data class ObdSessionEntity(
    @PrimaryKey val id: String,
    val vehicleProfileId: String?,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val adapterKind: String?,
    val adapterAddress: String?,
)

@Entity(
    tableName = "pid_samples",
    foreignKeys = [
        ForeignKey(
            entity = ObdSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("pid"), Index("timestampMillis")],
)
data class PidSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val pid: String,
    val value: Double,
    val unit: String,
    val timestampMillis: Long,
)

@Entity(
    tableName = "dtc_records",
    foreignKeys = [
        ForeignKey(
            entity = ObdSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("code")],
)
data class DtcRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val code: String,
    val description: String,
    val status: String,
)
