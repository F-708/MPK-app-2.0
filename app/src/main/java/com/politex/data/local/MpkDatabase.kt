package com.politex.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.politex.data.local.dao.LessonDao
import com.politex.data.local.dao.TaskDao
import com.politex.data.local.dao.TeacherLessonDao
import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.local.entity.TeacherLessonEntity

@Database(
    entities = [
        LessonEntity::class,
        StudentTaskEntity::class,
        TeacherLessonEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MpkDatabase : RoomDatabase() {

    abstract fun lessonDao(): LessonDao
    abstract fun taskDao(): TaskDao
    abstract fun teacherLessonDao(): TeacherLessonDao

    companion object {
        // internal, а не private: тест миграции создаёт базу v1 «на диске» под тем же именем
        internal const val DATABASE_NAME = "mpk_schedule.db"

        /**
         * v1 → v2: добавлена таблица расписания преподавателей (офлайн-режим).
         *
         * Миграция обязательна и явная: на `fallbackToDestructiveMigration` смена схемы
         * стёрла бы таблицу `student_tasks` — домашние задания, которые студент записал
         * руками и восстановить неоткуда. Здесь же добавляется только новая таблица,
         * старые данные не трогаются.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `teacher_lessons` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `teacherName` TEXT NOT NULL,
                        `surnameKey` TEXT NOT NULL,
                        `dateString` TEXT NOT NULL,
                        `dayOfWeek` INTEGER NOT NULL,
                        `lessonNumber` INTEGER NOT NULL,
                        `groups` TEXT NOT NULL,
                        `subject` TEXT NOT NULL,
                        `room` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_teacher_lessons_surnameKey_dateString` " +
                        "ON `teacher_lessons` (`surnameKey`, `dateString`)"
                )
            }
        }

        /**
         * v2 → v3 (D1/DAT-01): индексы на `lessons` под реальные запросы. Таблица держит
         * кэш ВСЕХ групп (тысячи строк), виджет читает её каждую минуту — без индексов каждый
         * запрос был full scan. Имена Room-генерируемые (index_<table>_<cols>) — должны
         * совпадать побайтово с ожидаемой схемой, иначе Room упадёт на валидации при открытии
         * (см. MigrationV2ToV3Test).
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_groupName_dayOfWeek` ON `lessons` (`groupName`, `dayOfWeek`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_groupName_dateString` ON `lessons` (`groupName`, `dateString`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_dateString` ON `lessons` (`dateString`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_roomFirst` ON `lessons` (`roomFirst`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_roomSecond` ON `lessons` (`roomSecond`)")
            }
        }

        @Volatile
        private var INSTANCE: MpkDatabase? = null

        fun getInstance(context: Context): MpkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MpkDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    // ponytail: оставлено как страховка от будущих незапланированных схем —
                    // для объявленных версий выше путь всегда идёт через MIGRATION_1_2
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
