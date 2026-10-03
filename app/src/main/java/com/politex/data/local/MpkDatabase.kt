package com.politex.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.politex.data.local.dao.GradeDao
import com.politex.data.local.dao.LessonDao
import com.politex.data.local.dao.MaterialDao
import com.politex.data.local.dao.TaskDao
import com.politex.data.local.dao.TaskReminderDao
import com.politex.data.local.dao.TeacherLessonDao
import com.politex.data.local.entity.GradeConfigEntity
import com.politex.data.local.entity.GradeEntryEntity
import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.MaterialAnnotationEntity
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
        @Suppress("DEPRECATION") MaterialFolderEntity::class,
        MaterialItemEntity::class,
        MaterialBookmarkEntity::class,
        MaterialAnnotationEntity::class,
        GradeEntryEntity::class,
        GradeConfigEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class MpkDatabase : RoomDatabase() {
    abstract fun lessonDao(): LessonDao

    abstract fun taskDao(): TaskDao

    abstract fun teacherLessonDao(): TeacherLessonDao

    abstract fun taskReminderDao(): TaskReminderDao

    abstract fun materialDao(): MaterialDao

    abstract fun gradeDao(): GradeDao

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
        private val migration1To2 =
            object : Migration(1, 2) {
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
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_teacher_lessons_surnameKey_dateString` " +
                            "ON `teacher_lessons` (`surnameKey`, `dateString`)",
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
        private val migration2To3 =
            object : Migration(2, 3) {
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
        private val migration3To4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `task_reminders` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `taskId` INTEGER NOT NULL,
                            `triggerAtMillis` INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_task_reminders_taskId` " +
                            "ON `task_reminders` (`taskId`)",
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
        private val migration4To5 =
            object : Migration(4, 5) {
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
                        """.trimIndent(),
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
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_material_items_folderId` " +
                            "ON `material_items` (`folderId`)",
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `material_bookmarks` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `itemId` INTEGER NOT NULL,
                            `page` INTEGER NOT NULL,
                            `label` TEXT NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_material_bookmarks_itemId` " +
                            "ON `material_bookmarks` (`itemId`)",
                    )
                }
            }

        /**
         * G1: v5 → v6 — «Оценки». Две НОВЫХ таблицы (`grade_entries`, `grade_config`),
         * существующие не трогаются: ДЗ, материалы и напоминания переживают миграцию.
         * Индексов нет (см. KDoc GradeEntryEntity), DEFAULT-выражений тоже — значения по
         * умолчанию заданы в Kotlin-конструкторах. Имена таблиц/колонок Room-генерируемые и
         * должны совпасть побайтово со схемой v6 (см. MigrationV5ToV6Test).
         */
        private val migration5To6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `grade_entries` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `subjectName` TEXT NOT NULL,
                            `category` TEXT NOT NULL,
                            `value` INTEGER NOT NULL,
                            `label` TEXT NOT NULL,
                            `dateIso` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `grade_config` (
                            `subjectName` TEXT NOT NULL,
                            `priorityJson` TEXT NOT NULL,
                            PRIMARY KEY(`subjectName`)
                        )
                        """.trimIndent(),
                    )
                }
            }

        /**
         * W11: v6 → v7 — материалы принадлежат ПРЕДМЕТУ, папки выпилены из UI.
         *
         * `material_items` получают `subjectName` (предмет папки владельца, JOIN) и `archived`
         * (1, если папка-владелец была архивом). Таблица `material_folders` и колонка
         * `folderId` НЕ дропаются: лишняя таблица/колонка Room не ломают, а деструктивная
         * правка рискует потерять данные. UI `folderId` больше не читает.
         *
         * DEFAULT-выражения повторяют @ColumnInfo(defaultValue=...) сущности побайтово —
         * иначе Room упадёт на валидации схемы при открытии (см. MigrationV6ToV7Test).
         */
        private val migration6To7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `material_items` ADD COLUMN `subjectName` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `material_items` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0")
                    // Перенос данных: предмет = предмет папки-владельца, архив = её isArchive.
                    db.execSQL(
                        "UPDATE `material_items` SET " +
                            "`subjectName` = COALESCE((SELECT f.`subjectName` FROM `material_folders` f WHERE f.`id` = `material_items`.`folderId`), ''), " +
                            "`archived` = COALESCE((SELECT f.`isArchive` FROM `material_folders` f WHERE f.`id` = `material_items`.`folderId`), 0)",
                    )
                }
            }

        @Volatile
        private var instance: MpkDatabase? = null


        /**
         * V7 → V8 (V9 аннотации + V10 правка текста + V18 веса/округление оценок).
         * Новая таблица material_annotations (unique itemId+pageIndex), колонка
         * material_items.textEdited и три колонки grade_config. DEFAULT-выражения
         * побайтово как в @ColumnInfo сущностей, иначе Room упадёт на валидации.
         */
        private val migration7To8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `material_annotations` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `itemId` INTEGER NOT NULL,
                            `pageIndex` INTEGER NOT NULL,
                            `strokesJson` TEXT NOT NULL,
                            `updatedAt` INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_material_annotations_itemId_pageIndex` " +
                            "ON `material_annotations` (`itemId`, `pageIndex`)",
                    )
                    db.execSQL("ALTER TABLE `material_items` ADD COLUMN `textEdited` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `grade_config` ADD COLUMN `weightsJson` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `grade_config` ADD COLUMN `roundingMode` TEXT NOT NULL DEFAULT '0.1'")
                    db.execSQL("ALTER TABLE `grade_config` ADD COLUMN `roundUp` INTEGER NOT NULL DEFAULT 0")
                }
            }

        /**
         * X9: v8 → v9 — grade_config.weightsEnabled (веса предмета вкл/выкл).
         * DEFAULT 1: у существующих предметов веса остаются включёнными.
         * DEFAULT-выражение побайтово как @ColumnInfo, иначе Room упадёт на валидации
         * (см. MigrationV8ToV9Test).
         */
        private val migration8To9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `grade_config` ADD COLUMN `weightsEnabled` INTEGER NOT NULL DEFAULT 1")
                }
            }

        fun getInstance(context: Context): MpkDatabase = instance ?: synchronized(this) {
            // QA#16: повторная проверка под замком обязательна — без неё два потока,
            // прошедшие первый `instance ?:` одновременно, построят ДВЕ базы (вторая
            // перезапишет instance, у подписчиков первой останутся висящие соединения).
            instance ?: run {
                val built =
                    Room
                        .databaseBuilder(
                            context.applicationContext,
                            MpkDatabase::class.java,
                            DATABASE_NAME,
                        ).addMigrations(migration1To2, migration2To3, migration3To4, migration4To5, migration5To6, migration6To7, migration7To8, migration8To9)
                        // ВНИМАНИЕ (QA#16): fallbackToDestructiveMigration СОЗНАТЕЛЬНО оставлен —
                        // краш при даунгрейде/неописанной схеме хуже потери кэша. Но он СТИРАЕТ
                        // ВСЕ данные пользователя (включая student_tasks — ДЗ, восстановлению не
                        // подлежит) при ЛЮБОЙ версии без явной миграции выше. Каждая новая версия
                        // схемы ОБЯЗАНА получать свою migrationNToN+1 в addMigrations.
                        .fallbackToDestructiveMigration()
                        .build()
                instance = built
                built
            }
        }
    }
}
