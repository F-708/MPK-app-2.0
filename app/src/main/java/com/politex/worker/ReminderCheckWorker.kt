package com.politex.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.politex.data.local.MpkDatabase
import com.politex.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * H1: периодическая (15 мин) доставка напоминаний о заданиях.
 *
 * Логика: берём все напоминания, у которых `triggerAtMillis <= now + 15мин` (due-окно воркера),
 * для каждого грузим задание; если задание ещё не выполнено — показываем уведомление.
 * Строку напоминания удаляем в любом случае (одноразовое): и после отправки, и если задание
 * уже выполнено/удалено (иначе «залипшее» напоминание долбило бы каждые 15 минут).
 *
 * Сеть не нужна — только локальная БД + уведомление. POST_NOTIFICATIONS проверяется внутри
 * NotificationHelper.showReminderNotification (на 13+ без разрешения просто не шлём).
 */
class ReminderCheckWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = MpkDatabase.getInstance(applicationContext)
        val reminderDao = db.taskReminderDao()
        val taskDao = db.taskDao()

        val until = System.currentTimeMillis() + LOOKAHEAD_MILLIS
        val due = reminderDao.getDueUntil(until)

        for (reminder in due) {
            val task = taskDao.getTaskById(reminder.taskId)
            if (task != null && !task.isCompleted) {
                NotificationHelper.showReminderNotification(applicationContext, task, reminder)
            }
            // одноразовое напоминание — удаляем после обработки
            reminderDao.deleteById(reminder.id)
        }
        Result.success()
    }

    companion object {
        const val WORK_NAME = "mpk_reminder_check_worker"

        // 15 минут — совпадает с периодом PeriodicWorkRequest, чтобы каждое напоминание
        // попадало ровно в одно due-окно (следующий запуск его уже удалит).
        private const val LOOKAHEAD_MILLIS = 15L * 60L * 1000L
    }
}
