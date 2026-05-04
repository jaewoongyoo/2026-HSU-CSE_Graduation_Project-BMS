package com.han.battery.data.storage

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.han.battery.data.model.BatteryLog

@Database(entities = [BatteryLog::class], version = 2, exportSchema = false)
abstract class BatteryDatabase : RoomDatabase() {
    abstract fun batteryDao(): BatteryDao

    companion object {
        const val DATABASE_NAME = "battery_db_v2"

        @Volatile
        private var INSTANCE: BatteryDatabase? = null

        fun getDatabase(context: Context): BatteryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BatteryDatabase::class.java,
                    DATABASE_NAME
                )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
