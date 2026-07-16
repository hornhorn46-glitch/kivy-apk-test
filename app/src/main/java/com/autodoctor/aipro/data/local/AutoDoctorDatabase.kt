package com.autodoctor.aipro.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        VehicleProfileEntity::class,
        ObdSessionEntity::class,
        PidSampleEntity::class,
        DtcRecordEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AutoDoctorDatabase : RoomDatabase() {
    abstract fun dao(): AutoDoctorDao

    companion object {
        fun create(context: Context): AutoDoctorDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AutoDoctorDatabase::class.java,
                "autodoctor.db",
            ).build()
        }
    }
}
