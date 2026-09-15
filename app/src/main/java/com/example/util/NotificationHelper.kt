package com.example.util

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
import com.example.MainActivity
import com.example.R

/**
 * Хелпер для создания каналов уведомлений и деликатной отправки пушей
 * о выходе расписания колледжа (МГПК).
 */
object NotificationHelper {

    const val CHANNEL_ID = "mpk_schedule_updates"
    const val CHANNEL_NAME = "Обновления расписания МГПК"
    const val NOTIFICATION_ID = 2026

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                importance
            ).apply {
                description = "Уведомления о публикации свежего расписания на следующий учебный день"
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Отправляет ровно один пуш о выходе расписания при наличии системных разрешений.
     */
    fun showScheduleReleaseNotification(
        context: Context,
        dayTitle: String,
        lessonCount: Int,
        targetDayOfWeek: Int,
        targetDate: String
    ) {
        createNotificationChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_OPEN_TAB", "SCHEDULE")
            putExtra("EXTRA_DAY_OF_WEEK", targetDayOfWeek)
            putExtra("EXTRA_TARGET_DATE", targetDate)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val lessonsWord = when {
            lessonCount % 10 == 1 && lessonCount % 100 != 11 -> "пара"
            lessonCount % 10 in 2..4 && lessonCount % 100 !in 12..14 -> "пары"
            else -> "пар"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mpk_emblem)
            .setContentTitle("Вышло расписание на $dayTitle")
            .setContentText("Назначено $lessonCount $lessonsWord. Нажмите, чтобы посмотреть кабинеты.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Назначено $lessonCount $lessonsWord. Нажмите, чтобы открыть расписание и проверить номера кабинетов.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (ignored: SecurityException) {
            // Защита от race-condition отзывов разрешений в Android
        }
    }
}
