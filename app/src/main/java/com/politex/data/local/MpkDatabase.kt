package com.politex.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.politex.data.local.dao.LessonDao
import com.politex.data.local.dao.TaskDao
import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.StudentTaskEntity

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
