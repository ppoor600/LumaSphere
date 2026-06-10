package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [HdrProject::class], version = 1, exportSchema = false)
abstract class HdrDatabase : RoomDatabase() {
    abstract fun hdrProjectDao(): HdrProjectDao

    companion object {
        @Volatile
        private var INSTANCE: HdrDatabase? = null

        fun getDatabase(context: Context): HdrDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HdrDatabase::class.java,
                    "hdr_eye_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
