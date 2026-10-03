package com.politex

import android.app.Application
import android.content.Context
import android.Manifest
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.local.entity.TaskReminderEntity
import com.politex.data.repository.TaskRepository
import com.politex.worker.deliverDueReminders
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * W31 (КРИТ-БАГ): «заказчик пишет напоминание в диалоге — после сохранения его нет».
 *
 * Два звена цепочки на реальной in-memory Room:
 *
 * 1. ROUND-TRIP СОХРАНЕНИЯ — ровно то, что делает хендлер TasksScreen.saveTaskAndReminders:
 *    `taskRepository.saveTask(entity)` (возвращает id вставленной строки) →
 *    `taskReminderDao.replaceForTask(id, …)` → повторное чтение `getForTask`.
 *    Реактивен на поломку любого звена: потерянный id, невызанный replaceForTask,
 *    неверная таблица — напоминание в БД не доедет.
 *
 * 2. W31 ROOT CAUSE — воркер доставки. `ReminderCheckWorker` раз в 15 минут берёт все
 *    напоминания с triggerAt <= now (QA#8: только НАСТУПИВШИЕ, без lookahead — будущие
 *    подберёт следующий прогон) и ПОСЛЕ ПОПЫТКИ доставки удаляет строку. Если
 *    POST_NOTIFICATIONS не выдано — уведомление молча не показывается,
 *    а строка удалялась ВСЁ РАВНО: напоминание, записанное в БД, исчезало в течение
 *    15 минут («не сохраняется»). Специфика: композер по умолчанию ставит «сегодня 18:00»,
 *    вечером такое напоминание уже в прошлом → сразу due → молчаливое удаление.
 *    Теперь недоставленное напоминание остаётся в БД и дождётся разрешения.
 *
 * (Полный compose-путь через TaskEditDialog под Robolectric не тестируется: TextField
 * в диалоговом окне не даёт тестовому Espresso уйти в idle — ограничение тестовой среды,
 * не приложения. Звенья диалог→onTaskSaved закрыты код-ревью, звенья ниже — этими тестами.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderSaveRoundTripTest {
    private lateinit var db: MpkDatabase
    private lateinit var repo: TaskRepository
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(ctx, MpkDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repo = TaskRepository(db.taskDao())
        // детерминизм: разрешение на уведомления закрыто (как у заказчика без POST_NOTIFICATIONS)
        shadowOf(ctx as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun task(
        id: Long = 0,
        title: String = "Упр. 4",
    ) = StudentTaskEntity(
        id = id,
        groupName = "41О",
        course = 4,
        subjectName = "ТОЭ",
        taskType = "Домашнее задание",
        title = title,
    )

    // ── 1. Round-trip сохранения: save → replaceForTask → повторное чтение ─────────

    @Test
    fun `новое задание с двумя напоминаниями сохраняет их под своим id`() = runBlocking {
        val now = System.currentTimeMillis()
        val t1 = now + 24 * 3_600_000L
        val t2 = now + 2 * 24 * 3_600_000L

        // ровно цепочка TasksScreen.saveTaskAndReminders: id от saveTask → replaceForTask
        val id = repo.saveTask(task())
        assertTrue("saveTask обязан вернуть id новой строки", id > 0)
        db.taskReminderDao().replaceForTask(
            id,
            listOf(
                TaskReminderEntity(taskId = id, triggerAtMillis = t1),
                TaskReminderEntity(taskId = id, triggerAtMillis = t2),
            ),
        )

        val saved = db.taskReminderDao().getForTask(id)
        assertEquals("W31: оба напоминания должны лежать в БД", 2, saved.size)
        assertEquals(setOf(t1, t2), saved.map { it.triggerAtMillis }.toSet())
        assertTrue("все строки привязаны к id задания", saved.all { it.taskId == id })
    }

    @Test
    fun `правка задания замещает напоминания атомарно без потери id`() = runBlocking {
        val id = repo.saveTask(task())
        val newTime = System.currentTimeMillis() + 3 * 24 * 3_600_000L

        // правка: saveTask возвращает ТОТ ЖЕ id (REPLACE по PK), replaceForTask меняет список
        val sameId = repo.saveTask(task(id = id, title = "Упр. 4 (исправлено)"))
        assertEquals("id при правке не должен меняться", id, sameId)
        db.taskReminderDao().replaceForTask(sameId, listOf(TaskReminderEntity(taskId = sameId, triggerAtMillis = newTime)))

        val saved = db.taskReminderDao().getForTask(id)
        assertEquals(1, saved.size)
        assertEquals(newTime, saved.first().triggerAtMillis)
        assertEquals(id, saved.first().taskId)
    }

    // ── 2. W31 root cause: воркер не имеет права удалять недоставленное напоминание ──

    @Test
    fun `недоставленное напоминание переживает запуск воркера без POST_NOTIFICATIONS`() = runBlocking {
        val id = repo.saveTask(task())
        // «сегодня 18:00» вечером — уже прошло: попадает в due-окно воркера
        val pastTime = System.currentTimeMillis() - 60_000L
        db.taskReminderDao().replaceForTask(id, listOf(TaskReminderEntity(taskId = id, triggerAtMillis = pastTime)))

        deliverDueReminders(db, context)

        val survivors = db.taskReminderDao().getForTask(id)
        assertEquals(
            "W31: без разрешения уведомления строка обязана остаться (иначе напоминание «не сохраняется»)",
            1,
            survivors.size,
        )
    }

    @Test
    fun `выполненное задание — напоминание удаляется без доставки`() = runBlocking {
        val id = repo.saveTask(task())
        db.taskDao().setTaskCompleted(id, true)
        db.taskReminderDao().replaceForTask(
            id,
            listOf(TaskReminderEntity(taskId = id, triggerAtMillis = System.currentTimeMillis() - 60_000L)),
        )

        deliverDueReminders(db, context)

        assertTrue("выполненное задание — напоминание чистится", db.taskReminderDao().getForTask(id).isEmpty())
    }

    @Test
    fun `будущее напоминание вне due-окна воркером не трогается`() = runBlocking {
        val id = repo.saveTask(task())
        val future = System.currentTimeMillis() + 24 * 3_600_000L
        db.taskReminderDao().replaceForTask(id, listOf(TaskReminderEntity(taskId = id, triggerAtMillis = future)))

        deliverDueReminders(db, context)

        assertEquals(1, db.taskReminderDao().getForTask(id).size)
    }

    @Test
    fun `QA8 напоминание через 10 минут не доставляется рано даже у выполненного задания`() = runBlocking {
        val id = repo.saveTask(task())
        db.taskDao().setTaskCompleted(id, true)
        // +10 мин: старый lookahead (now+15мин) уже считал такое напоминание due и удалял
        // строку ДО срока; теперь воркер берёт только наступившие (triggerAt <= now).
        val soon = System.currentTimeMillis() + 10 * 60_000L
        db.taskReminderDao().replaceForTask(id, listOf(TaskReminderEntity(taskId = id, triggerAtMillis = soon)))

        deliverDueReminders(db, context)

        assertEquals("не наступило — не тронуто", 1, db.taskReminderDao().getForTask(id).size)
    }
}
