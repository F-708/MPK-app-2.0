package com.politex.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.politex.MainActivity
import com.politex.R
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.local.entity.TaskReminderEntity

object NotificationHelper {
    const val CHANNEL_ID = "mpk_schedule_updates"
    const val CHANNEL_NAME = "Обновления расписания МГПК"
    const val NOTIFICATION_ID = 2026

    // H1: отдельный канал напоминаний о заданиях — IMPORTANCE_HIGH (звуковой/heads-up),
    // чтобы «сдать до завтра» не терялось в бесшумном канале расписания.
    const val REMINDER_CHANNEL_ID = "mpk_reminders"
    const val REMINDER_CHANNEL_NAME = "Напоминания о заданиях"
    private const val REMINDER_NOTIFICATION_BASE = 3000

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    importance,
                ).apply {
                    description = "Уведомления о публикации свежего расписания на следующий учебный день"
                    enableVibration(true)
                }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun showScheduleReleaseNotification(
        context: Context,
        dayTitle: String,
        lessonCount: Int,
        targetDayOfWeek: Int,
        targetDate: String,
    ) {
        createNotificationChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val openAppIntent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_OPEN_TAB", "SCHEDULE")
                putExtra("EXTRA_DAY_OF_WEEK", targetDayOfWeek)
                putExtra("EXTRA_TARGET_DATE", targetDate)
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        // Уроки, не пары: в колледже занятие называют уроком (1 пара = 2 урока, но в расписании — «урок»)
        val lessonsWord =
            when {
                lessonCount % 10 == 1 && lessonCount % 100 != 11 -> "урок"
                lessonCount % 10 in 2..4 && lessonCount % 100 !in 12..14 -> "урока"
                else -> "уроков"
            }

        // dayTitle приходит в винительном падеже («в среду»); пустая строка — день неизвестен
        val title =
            if (dayTitle.isNotBlank()) {
                "Занятия в $dayTitle: $lessonCount $lessonsWord"
            } else {
                "Занятия на завтра: $lessonCount $lessonsWord"
            }

        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mpk_emblem)
                .setContentTitle(title)
                .setContentText("Расписание опубликовано. Нажмите, чтобы посмотреть.")
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText("Расписание опубликовано. Нажмите, чтобы посмотреть."),
                ).setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (ignored: SecurityException) {
        }
    }

    /** H1: канал напоминаний — IMPORTANCE_HIGH (heads-up + звук). */
    fun createReminderChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    REMINDER_CHANNEL_ID,
                    REMINDER_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Напоминания о приближающемся сроке сдачи задания"
                    enableVibration(true)
                }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * H1: уведомление по напоминанию задания. Текст «Напоминание: <title> (<предмет>)»,
     * тап открывает вкладку TASKS. Учёт POST_NOTIFICATIONS — как в showScheduleReleaseNotification:
     * на Android 13+ без выданного разрешения просто выходим (без crash).
     * id уведомления уникален на напоминание, чтобы несколько напоминаний не перезаписывали друг друга.
     */
    fun showReminderNotification(
        context: Context,
        task: StudentTaskEntity,
        reminder: TaskReminderEntity,
    ) {
        createReminderChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val openAppIntent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_OPEN_TAB", "TASKS")
            }
        val requestCode = (REMINDER_NOTIFICATION_BASE + reminder.id).toInt()
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                requestCode,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val subject = task.shortSubjectName.ifBlank { task.subjectName }
        val title = "Напоминание: ${task.title}"
        val body = if (subject.isNotBlank()) "($subject)" else "Задание"

        val notification =
            NotificationCompat
                .Builder(context, REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mpk_emblem)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$title $body"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        try {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
        } catch (ignored: SecurityException) {
        }
    }
}
