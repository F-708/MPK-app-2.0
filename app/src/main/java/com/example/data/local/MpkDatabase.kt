package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.LessonDao
import com.example.data.local.dao.TaskDao
import com.example.data.local.entity.LessonEntity
import com.example.data.local.entity.StudentTaskEntity

@Database(
    entities = [
        LessonEntity::class,
        StudentTaskEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MpkDatabase : RoomDatabase() {

    abstract fun lessonDao(): LessonDao
    abstract fun taskDao(): TaskDao

    companion object {
        private const val DATABASE_NAME = "mpk_schedule.db"

        @Volatile
        private var INSTANCE: MpkDatabase? = null

        fun getInstance(context: Context): MpkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MpkDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
