package com.politex.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * H1: напоминание о задании. Отдельная таблица, а не колонка в `student_tasks`:
 * у одного задания бывает НЕСКОЛЬКО напоминаний (разные даты/время), а колонка
 * `deadlineDate` хранит только ISO-дату сдачи без времени.
 *
 * `triggerAtMillis` — абсолютный момент (epoch millis, локальное время устройства),
 * когда надо показать уведомление. Воркер ReminderCheckWorker раз в 15 минут берёт
 * все строки с `triggerAtMillis <= now+15мин`, шлёт уведомление и УДАЛЯЕТ строку —
 * напоминание одноразовое, «не отправленные» = просто ещё не удалённые.
 *
 * Индекс по `taskId`: getForTask/replaceForTask фильтруют именно по нему.
 */
@Entity(
    tableName = "task_reminders",
    indices = [Index("taskId")]
)
data class TaskReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long,
    val triggerAtMillis: Long
)
