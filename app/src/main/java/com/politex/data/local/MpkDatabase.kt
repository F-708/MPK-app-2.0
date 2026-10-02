package com.politex.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.politex.data.local.dao.LessonDao
import com.politex.data.local.dao.MaterialDao
import com.politex.data.local.dao.TaskDao
import com.politex.data.local.dao.TaskReminderDao
import com.politex.data.local.dao.TeacherLessonDao
import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.MaterialBookmarkEntity
import com.politex.data.local.entity.MaterialFolderEntity
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.local.entity.TaskReminderEntity
import com.politex.data.local.entity.TeacherLessonEntity

@Database(
    entities = [
        LessonEntity::class,
        StudentTaskEntity::class,
        TeacherLessonEntity::class,
        TaskReminderEntity::class,
        MaterialFolderEntity::class,
        MaterialItemEntity::class,
        MaterialBookmarkEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class MpkDatabase : RoomDatabase() {

    abstract fun lessonDao(): LessonDao
    abstract fun taskDao(): TaskDao
    abstract fun teacherLessonDao(): TeacherLessonDao
    abstract fun taskReminderDao(): TaskReminderDao
    abstract fun materialDao(): MaterialDao

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

        /**
         * v3 → v4 (H1): таблица напоминаний о заданиях `task_reminders`. Добавляется только
         * НОВАЯ таблица + индекс — `student_tasks`/`lessons`/`teacher_lessons` не трогаются,
         * ДЗ студента переживает миграцию. Имена таблицы/индекса Room-генерируемые
         * (index_task_reminders_taskId) — должны совпасть побайтово с @Entity(indices=...),
         * иначе Room упадёт на валидации схемы при открытии (см. MigrationV3ToV4Test).
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `task_reminders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `taskId` INTEGER NOT NULL,
                        `triggerAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_task_reminders_taskId` " +
                        "ON `task_reminders` (`taskId`)"
                )
            }
        }

        /**
         * M1: v4 → v5 — «Материальная база». Три НОВЫХ таблицы (`material_folders`,
         * `material_items`, `material_bookmarks`) + индексы folderId/itemId. Существующие
         * `lessons`/`student_tasks`/`teacher_lessons`/`task_reminders` не трогаются: ДЗ и
         * напоминания студента переживают миграцию.
         *
         * Имена таблиц/колонок/индексов Room-генерируемые (index_<table>_<col>) и должны
         * совпасть побайтово с ожидаемой схемой v5, иначе Room упадёт на валидации при
         * открытии у всех обновившихся (см. MigrationV4ToV5Test).
         *
         * Значения по умолчанию (`isArchive`, `textContent`, …) заданы в Kotlin-конструкторах
         * сущностей, а НЕ через @ColumnInfo(defaultValue=...) — поэтому здесь нет DEFAULT-выражений.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `material_folders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `subjectName` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `isArchive` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `material_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `folderId` INTEGER NOT NULL,
                        `kind` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `ext` TEXT NOT NULL,
                        `uriPath` TEXT NOT NULL,
                        `pageCount` INTEGER NOT NULL,
                        `lastPage` INTEGER NOT NULL,
                        `rotationDeg` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `textContent` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_material_items_folderId` " +
                        "ON `material_items` (`folderId`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `material_bookmarks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `itemId` INTEGER NOT NULL,
                        `page` INTEGER NOT NULL,
                        `label` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_material_bookmarks_itemId` " +
                        "ON `material_bookmarks` (`itemId`)"
                )
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
