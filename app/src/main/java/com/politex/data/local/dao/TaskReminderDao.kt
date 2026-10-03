package com.politex.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.politex.data.local.entity.TaskReminderEntity

/**
 * H1: DAO напоминаний. `getDueUntil` — ядро due-логики воркера: возвращает все строки,
 * у которых `triggerAtMillis <= untilMillis`. QA#8: воркер передаёт until = now — только
 * НАСТУПИВШИЕ напоминания, будущие догонит следующий периодический запуск (15 мин).
 * Сюда же попадают просроченные (triggerAt в прошлом) — если приложение было закрыто в
 * момент срабатывания, напоминание всё равно придёт при следующем запуске воркера.
 * Отправка одноразовая: после уведомления строка удаляется, поэтому
 * «не отправленные» = все, что ещё в таблице.
 */
@Dao
interface TaskReminderDao {
    @Query("SELECT * FROM task_reminders WHERE taskId = :taskId ORDER BY triggerAtMillis ASC")
    suspend fun getForTask(taskId: Long): List<TaskReminderEntity>

    @Query("SELECT * FROM task_reminders WHERE triggerAtMillis <= :untilMillis ORDER BY triggerAtMillis ASC")
    suspend fun getDueUntil(untilMillis: Long): List<TaskReminderEntity>

    @Insert
    suspend fun insertAll(reminders: List<TaskReminderEntity>)

    @Query("DELETE FROM task_reminders WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Long)

    @Query("DELETE FROM task_reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * H1: атомарная замена напоминаний задания при сохранении — deleteForTask + insertAll
     * одной транзакцией, чтобы между ними воркер не увидел пустой список и не потерял напоминания.
     */
    @Transaction
    suspend fun replaceForTask(
        taskId: Long,
        reminders: List<TaskReminderEntity>,
    ) {
        deleteForTask(taskId)
        if (reminders.isNotEmpty()) insertAll(reminders)
    }
}
