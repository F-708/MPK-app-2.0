package com.politex.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.politex.data.local.MpkDatabase
import com.politex.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * H1: периодическая (15 мин) доставка напоминаний о заданиях.
 *
 * QA#8: берём ТОЛЬКО наступившие напоминания (`triggerAtMillis <= now`). Раньше окно было
 * `now + 15 мин` — напоминание доставлялось ДО срока (вплоть до 15 минут рано). Lookahead
 * не нужен: воркер периодический (15 мин) и подберёт напоминание на следующем прогоне,
 * когда срок реально наступит.
 * Для каждого due грузим задание; если задание ещё не выполнено — показываем уведомление.
 * Строку напоминания удаляем после доставки; если задание уже выполнено/удалено — тоже
 * (иначе «залипшее» напоминание долбило бы каждые 15 минут).
 *
 * W31: если POST_NOTIFICATIONS НЕ выдано — уведомление показано не будет, и прежний код
 * всё равно удалял строку: записанное в БД напоминание бесследно исчезало в течение
 * 15 минут («напоминание не сохраняется»). Теперь недоставленное напоминание ОСТАЁТСЯ
 * в таблице и дождётся выдачи разрешения — затем доставится и удалится как обычно.
 *
 * Сеть не нужна — только локальная БД + уведомление. POST_NOTIFICATIONS проверяется здесь
 * (для решения об удалении строки) и внутри NotificationHelper.showReminderNotification.
 */
class ReminderCheckWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = MpkDatabase.getInstance(applicationContext)
        deliverDueReminders(db, applicationContext)
        Result.success()
    }

    companion object {
        const val WORK_NAME = "mpk_reminder_check_worker"
    }
}

/** W31: доставка due-напоминаний. Выделена из воркера для прямого юнит-теста. */
internal suspend fun deliverDueReminders(
    db: MpkDatabase,
    context: Context,
) {
    val reminderDao = db.taskReminderDao()
    val taskDao = db.taskDao()

    // QA#8: только наступившие (triggerAt <= now), без lookahead — будущие напоминания
    // догонит следующий периодический запуск воркера (15 мин).
    val due = reminderDao.getDueUntil(System.currentTimeMillis())

    for (reminder in due) {
        val task = taskDao.getTaskById(reminder.taskId)
        when {
            // задание удалено/выполнено — доставлять нечего, повторять бессмысленно
            task == null || task.isCompleted -> reminderDao.deleteById(reminder.id)

            // доставлено — строка больше не нужна (напоминание одноразовое)
            canPostNotifications(context) -> {
                NotificationHelper.showReminderNotification(context, task, reminder)
                reminderDao.deleteById(reminder.id)
            }

            // W31: не доставлено из-за отсутствия разрешения — строку НЕ удаляем:
            // напоминание должно пережить запуск воркера и дождаться разрешения
            else -> {}
        }
    }
}

/** W31: same-чек, что внутри NotificationHelper (на 13+ без разрешения уведомление не шлём). */
internal fun canPostNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
