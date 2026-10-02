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
 * Миграция v1 → v2 (добавление таблицы расписания преподавателей).
 *
 * Зачем отдельный тест: база собирается с `fallbackToDestructiveMigration`, поэтому
 * забытая миграция не падает, а ТИХО СТИРАЕТ данные. Для этой таблицы стёрлись бы
 * `student_tasks` — домашние задания, записанные студентом руками. Тест открывает
 * реальную базу версии 1 со строкой задания и проверяет, что после миграции
 * задание на месте, а новая таблица доступна для записи.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationV1ToV2Test {

    // То же имя файла, что у боевой базы: иначе MpkDatabase.getInstance открыл бы
// свежую базу и миграция не проверялась бы вовсе
    private val dbName = com.politex.data.local.MpkDatabase.DATABASE_NAME

    /** Схема v1 — в точности так, как её создал бы Room для версии 1. */
    private fun createV1Database(context: Context) {
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
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
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.use { db ->
            db.execSQL("PRAGMA user_version = 1")
            db.execSQL(
                "INSERT INTO student_tasks " +
                    "(groupName, course, subjectName, taskType, title, description, isCompleted, deadlineDate, createdAt) " +
                    "VALUES ('41О', 4, 'ТОЭ', 'Домашнее задание', 'Лаба по ТОЭ', '', 0, '', 1234567890)"
            )
        }
    }

    /**
     * Схема v1 «на диске» и открытие её БОЕВОЙ базой — той самой, что использует
     * приложение, через её singleton. Так тест проверяет реальную цепочку
     * «старая база → MpkDatabase.getInstance → миграция», а не копию миграции
     * в тесте, которая могла бы отличаться от продовой.
     */
    @Test
    fun `задание пользователя переживает миграцию v1 в v2`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        createV1Database(context)

        // INSTANCE — статический singleton: другой миграционный тест в той же JVM мог
        // закэшировать инстанс на своей базе, и тогда getInstance вернул бы ЕГО, а не нашу
        // свежую v1-базу (миграция не проверялась бы вовсе). Сбрасываем рефлекшеном —
        // приём тот же, что в MigrationV2ToV3Test/MigrationV3ToV4Test.
        resetSingleton()

        // Единственная точка, где объявлена миграция, — companion MpkDatabase
        val db = com.politex.data.local.MpkDatabase.getInstance(context)

        val tasks = kotlinx.coroutines.runBlocking { db.taskDao().getAllTasks() }
        assertEquals("задание должно пережить миграцию", 1, tasks.size)
        assertEquals("Лаба по ТОЭ", tasks.first().title)
        assertEquals("41О", tasks.first().groupName)

        // новая таблица доступна и пуста
        val teacherRows = kotlinx.coroutines.runBlocking {
            db.teacherLessonDao().getForSurnameOnDate("ламкина", "16.09.2026")
        }
        assertTrue("новая таблица должна существовать и быть пустой", teacherRows.isEmpty())

        db.close()
        resetSingleton()
    }

    /** Сброс статического INSTANCE, чтобы getInstance открыл именно нашу свежую v1-базу. */
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