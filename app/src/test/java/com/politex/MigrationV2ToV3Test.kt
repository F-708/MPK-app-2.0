package com.politex

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Миграция v2 → v3 (D1/DAT-01: индексы на `lessons`).
 *
 * Зачем: база с `fallbackToDestructiveMigration` — ошибка в миграции (или её отсутствие)
 * не падает, а ТИХО СТИРАЕТ `student_tasks` (ДЗ студента). Плюс Room при открытии сверяет
 * фактическую схему с ожидаемой (включая имена индексов) — расхождение = crash у всех
 * обновившихся. Тест создаёт реальную базу v2, открывает боевым getInstance и проверяет:
 * данные на месте, индексы созданы с Room-именами.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationV2ToV3Test {

    private val dbName = com.politex.data.local.MpkDatabase.DATABASE_NAME

    // Общая константа — вставляется в SQL и используется в запросе, чтобы байты совпали
    // (иначе кириллическая «О» в литерале INSERT и в параметре запроса могут разойтись).
    private val group = "41О"

    /** Схема v2 в точности как создал бы Room версии 2 (lessons БЕЗ индексов + teacher_lessons). */
    private fun createV2Database(context: Context) {
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `lessons` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`groupName` TEXT NOT NULL, `dayOfWeek` INTEGER NOT NULL, " +
                                "`lessonNumber` INTEGER NOT NULL, `timeStart` TEXT NOT NULL, " +
                                "`timeEnd` TEXT NOT NULL, `subjectRaw` TEXT NOT NULL, " +
                                "`roomFirst` TEXT NOT NULL, `teacherFirst` TEXT NOT NULL, " +
                                "`roomSecond` TEXT NOT NULL, `teacherSecond` TEXT NOT NULL, " +
                                "`isSplit` INTEGER NOT NULL, `dateString` TEXT NOT NULL)"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `student_tasks` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`groupName` TEXT NOT NULL, `course` INTEGER NOT NULL, " +
                                "`subjectName` TEXT NOT NULL, `taskType` TEXT NOT NULL, " +
                                "`title` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                                "`isCompleted` INTEGER NOT NULL, `deadlineDate` TEXT NOT NULL, " +
                                "`createdAt` INTEGER NOT NULL)"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `teacher_lessons` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`teacherName` TEXT NOT NULL, `surnameKey` TEXT NOT NULL, " +
                                "`dateString` TEXT NOT NULL, `dayOfWeek` INTEGER NOT NULL, " +
                                "`lessonNumber` INTEGER NOT NULL, `groups` TEXT NOT NULL, " +
                                "`subject` TEXT NOT NULL, `room` TEXT NOT NULL)"
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_teacher_lessons_surnameKey_dateString` " +
                                "ON `teacher_lessons` (`surnameKey`, `dateString`)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.use { db ->
            db.execSQL("PRAGMA user_version = 2")
            db.execSQL(
                "INSERT INTO student_tasks " +
                    "(groupName, course, subjectName, taskType, title, description, isCompleted, deadlineDate, createdAt) " +
                    "VALUES ('$group', 4, 'ТОЭ', 'Домашнее задание', 'Лаба по ТОЭ', '', 0, '', 1234567890)"
            )
            db.execSQL(
                "INSERT INTO lessons " +
                    "(groupName, dayOfWeek, lessonNumber, timeStart, timeEnd, subjectRaw, roomFirst, " +
                    "teacherFirst, roomSecond, teacherSecond, isSplit, dateString) " +
                    "VALUES ('$group', 3, 1, '08:15', '09:00', 'Математика', '229', 'Иванов И.И.', '', '', 0, '01.10.2026')"
            )
        }
    }

    @Test
    fun `данные и схема переживают миграцию v2 в v3`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        createV2Database(context)

        // MpkDatabase.INSTANCE — статический singleton. Другой миграционный тест
        // (MigrationV1ToV2Test) в той же JVM уже закэшировал инстанс на СВОЕЙ базе,
        // поэтому getInstance вернул бы её, а не нашу свежую v2. Сбрасываем рефлекшеном.
        resetSingleton()

        // Боевой singleton: Room прогонит MIGRATION_2_3 и сверит схему с @Entity(indices=...)
        val db = com.politex.data.local.MpkDatabase.getInstance(context)

        val tasks = kotlinx.coroutines.runBlocking { db.taskDao().getAllTasks() }
        assertEquals("ДЗ должно пережить миграцию", 1, tasks.size)
        assertEquals("Лаба по ТОЭ", tasks.first().title)

        val lessons = kotlinx.coroutines.runBlocking { db.lessonDao().getLessonsForDaySync(group, 3) }
        assertEquals("урок должен пережить миграцию", 1, lessons.size)
        assertEquals("01.10.2026", lessons.first().dateString)

        // Индексы созданы с Room-именами (проверяем через sqlite_master)
        val indexNames = mutableSetOf<String>()
        db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='lessons'")
            .use { c ->
                while (c.moveToNext()) indexNames.add(c.getString(0))
            }
        for (expected in listOf(
            "index_lessons_groupName_dayOfWeek",
            "index_lessons_groupName_dateString",
            "index_lessons_dateString",
            "index_lessons_roomFirst",
            "index_lessons_roomSecond"
        )) {
            assertTrue("индекс $expected должен существовать, есть: $indexNames", indexNames.contains(expected))
        }
    }

    /** Сброс статического INSTANCE, чтобы getInstance открыл ИМЕННО нашу свежую v2-базу. */
    private fun resetSingleton() {
        try {
            val cls = Class.forName("com.politex.data.local.MpkDatabase")
            val field = cls.getDeclaredField("INSTANCE")
            field.isAccessible = true
            field.set(null, null)
        } catch (_: Exception) {
            // если поля нет/недоступно — тест и так упадёт на assertEquals ниже с ясной причиной
        }
    }
}
