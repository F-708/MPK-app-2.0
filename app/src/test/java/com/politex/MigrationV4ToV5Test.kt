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
 * M1 (Задача 2.2): миграция v4 → v5 — «Материальная база».
 *
 * Зачем отдельный тест: у базы включён `fallbackToDestructiveMigration`, поэтому отсутствующая
 * или кривая миграция НЕ падает, а тихо стирает `student_tasks` (ДЗ, которое студент записал
 * руками) и `task_reminders`. Плюс Room при открытии сверяет фактическую схему с ожидаемой
 * (имена таблиц, колонок, индексов) — расхождение = crash у всех обновившихся.
 *
 * Тест создаёт РЕАЛЬНУЮ базу v4 на диске (приём из MigrationV3ToV4Test), открывает её БОЕВЫМ
 * getInstance и проверяет: старые данные переживают, три новые таблицы и индексы созданы.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationV4ToV5Test {

    private val dbName = com.politex.data.local.MpkDatabase.DATABASE_NAME
    private val group = "41О"

    /** Схема v4 в точности как создал бы Room версии 4 (см. app/schemas/…/4.json). */
    private fun createV4Database(context: Context) {
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
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
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_groupName_dayOfWeek` ON `lessons` (`groupName`, `dayOfWeek`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_groupName_dateString` ON `lessons` (`groupName`, `dateString`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_dateString` ON `lessons` (`dateString`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_roomFirst` ON `lessons` (`roomFirst`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_roomSecond` ON `lessons` (`roomSecond`)")
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
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `task_reminders` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`taskId` INTEGER NOT NULL, `triggerAtMillis` INTEGER NOT NULL)"
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_task_reminders_taskId` " +
                                "ON `task_reminders` (`taskId`)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.use { db ->
            db.execSQL("PRAGMA user_version = 4")
            db.execSQL(
                "INSERT INTO student_tasks " +
                    "(groupName, course, subjectName, taskType, title, description, isCompleted, deadlineDate, createdAt) " +
                    "VALUES ('$group', 4, 'ТОЭ', 'Домашнее задание', 'Лаба по ТОЭ', '', 0, '2026-10-05', 1234567890)"
            )
            db.execSQL(
                "INSERT INTO lessons " +
                    "(groupName, dayOfWeek, lessonNumber, timeStart, timeEnd, subjectRaw, roomFirst, " +
                    "teacherFirst, roomSecond, teacherSecond, isSplit, dateString) " +
                    "VALUES ('$group', 3, 1, '08:15', '09:00', 'Математика', '229', 'Иванов И.И.', '', '', 0, '01.10.2026')"
            )
            // напоминание привязано к ДЗ: если миграция сотрёт student_tasks, оно повиснет в воздухе
            db.execSQL("INSERT INTO task_reminders (taskId, triggerAtMillis) VALUES (1, 1759650000000)")
        }
    }

    @Test
    fun `данные и схема переживают миграцию v4 в v5`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        createV4Database(context)

        // INSTANCE — статический singleton: другой миграционный тест в той же JVM мог
        // закэшировать инстанс на своей базе. Сбрасываем рефлекшеном, чтобы getInstance
        // открыл ИМЕННО нашу свежую v4-базу (приём из MigrationV3ToV4Test).
        resetSingleton()

        val db = com.politex.data.local.MpkDatabase.getInstance(context)

        // Старые данные пережили миграцию
        val tasks = kotlinx.coroutines.runBlocking { db.taskDao().getAllTasks() }
        assertEquals("ДЗ должно пережить миграцию", 1, tasks.size)
        assertEquals("Лаба по ТОЭ", tasks.first().title)
        assertEquals("2026-10-05", tasks.first().deadlineDate)

        val lessons = kotlinx.coroutines.runBlocking { db.lessonDao().getLessonsForDaySync(group, 3) }
        assertEquals("урок должен пережить миграцию", 1, lessons.size)
        assertEquals("01.10.2026", lessons.first().dateString)

        val reminders = kotlinx.coroutines.runBlocking { db.taskReminderDao().getDueUntil(Long.MAX_VALUE) }
        assertEquals("напоминание должно пережить миграцию", 1, reminders.size)
        assertEquals(1L, reminders.first().taskId)

        // Новые таблицы созданы и запросимы (Room не упал на валидации схемы — уже доказательство,
        // но проверяем и имена явно: падение валидации зависит от порядка открытия)
        val tables = queryNames(db, "SELECT name FROM sqlite_master WHERE type='table'")
        listOf("material_folders", "material_items", "material_bookmarks").forEach { expected ->
            assertTrue("таблица $expected должна существовать, есть: $tables", tables.contains(expected))
        }

        val indices = queryNames(db, "SELECT name FROM sqlite_master WHERE type='index'")
        listOf("index_material_items_folderId", "index_material_bookmarks_itemId").forEach { expected ->
            assertTrue("индекс $expected должен существовать, есть: $indices", indices.contains(expected))
        }

        // materialDao работает на мигрированной базе (вставка + чтение + значения по умолчанию)
        val materialDao = db.materialDao()
        val folderId = kotlinx.coroutines.runBlocking {
            materialDao.insertFolder(
                com.politex.data.local.entity.MaterialFolderEntity(subjectName = "ТОЭ", name = "Лекции")
            )
        }
        val itemId = kotlinx.coroutines.runBlocking {
            materialDao.insertItem(
                com.politex.data.local.entity.MaterialItemEntity(
                    folderId = folderId,
                    kind = "FILE",
                    title = "Лекция 1",
                    fileName = "lek1.pdf",
                    ext = "pdf",
                    uriPath = "/data/user/0/x/files/materials/1.pdf"
                )
            )
        }
        val saved = kotlinx.coroutines.runBlocking { materialDao.getItemById(itemId) }
        assertEquals("материал должен читаться из мигрированной базы", "Лекция 1", saved?.title)
        val savedFolder = kotlinx.coroutines.runBlocking { materialDao.getFolderById(folderId) }
        org.junit.Assert.assertFalse("isArchive по умолчанию = false", savedFolder?.isArchive == true)
        assertEquals("textContent по умолчанию пустой", "", saved?.textContent)
        assertEquals("pageCount по умолчанию 0", 0, saved?.pageCount)

        val bookmarks = kotlinx.coroutines.runBlocking { materialDao.getBookmarksForItem(itemId) }
        assertTrue("material_bookmarks существует и пустая", bookmarks.isEmpty())

        db.close()
        resetSingleton()
    }

    private fun queryNames(db: com.politex.data.local.MpkDatabase, sql: String): Set<String> {
        val out = mutableSetOf<String>()
        db.openHelper.readableDatabase.query(sql).use { c -> while (c.moveToNext()) out.add(c.getString(0)) }
        return out
    }

    /** Сброс статического INSTANCE, чтобы getInstance открыл именно нашу свежую v4-базу. */
    private fun resetSingleton() {
        try {
            val cls = Class.forName("com.politex.data.local.MpkDatabase")
            val field = cls.getDeclaredField("INSTANCE")
            field.isAccessible = true
            field.set(null, null)
        } catch (_: Exception) {
            // если поля нет/недоступно — тест упадёт ниже с ясной причиной
        }
    }
}
