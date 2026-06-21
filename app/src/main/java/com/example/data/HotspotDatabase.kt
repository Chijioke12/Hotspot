package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [HotspotDevice::class, HotspotLog::class], version = 1, exportSchema = false)
abstract class HotspotDatabase : RoomDatabase() {
    abstract val dao: HotspotDao

    companion object {
        @Volatile
        private var INSTANCE: HotspotDatabase? = null

        fun getDatabase(context: Context): HotspotDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HotspotDatabase::class.java,
                    "hotspot_monitor_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
