package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [StudyVideo::class], version = 1, exportSchema = false)
abstract class StudyDatabase : RoomDatabase() {
    abstract fun studyVideoDao(): StudyVideoDao

    companion object {
        @Volatile
        private var INSTANCE: StudyDatabase? = null

        fun getDatabase(context: Context): StudyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StudyDatabase::class.java,
                    "study_controller_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
