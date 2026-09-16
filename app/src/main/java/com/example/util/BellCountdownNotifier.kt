package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.widget.BellCountdownWidgetProvider
import com.example.widget.WidgetUpdateHelper

/**
 * Постоянное уведомление «До звонка» — висит в шторке, как навигация в картах.
 *
 * Включено по умолчанию выключено. Обновляется раз в минуту в учебное время
 * тем же алармом, что и виджет. Стиль оформления выбирается в настройках.
 */
object BellCountdownNotifier {

    /** id канала уведомления (создаётся отдельно, низкий приоритет — без звука). */
    const val CHANNEL_ID = "mpk_bell_countdown"
    const val CHANNEL_NAME = "До звонка"

    /** Фиксированный id постоянного уведомления. */
    const val NOTIFICATION_ID = 2027

    private const val PREF_ENABLED = "bell_notification_enabled"
    private const val PREF_STYLE = "bell_notification_style"

    /**
     * Стили оформления строки уведомления.
     * @param titleFormat строка-заголовок; {label} — «до звонка»/«до 3 урока»
     */
    enum class Style(val id: String, val title: String) {
        COMPACT("compact", "Компактный"),
        WITH_APP_NAME("app_name", "С названием приложения"),
        WITH_LESSON("lesson", "С номером урока"),
        WITH_PROGRESS("progress", "С полосой прогресса");

        companion object {
            fun from(id: String?): Style = entries.firstOrNull { it.id == id } ?: COMPACT
        }
    }

    // --------------------------- Настройки ---------------------------

    fun isEnabled(context: Context): Boolean =
        WidgetUpdateHelper.getPrefs(context).getBoolean(PREF_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        WidgetUpdateHelper.getPrefs(context).edit().putBoolean(PREF_ENABLED, enabled).apply()
        if (enabled) {
            show(context)
        } else {
            cancel(context)
        }
    }

    fun getStyle(context: Context): Style =
        Style.from(WidgetUpdateHelper.getPrefs(context).getString(PREF_STYLE, null))

    fun setStyle(context: Context, style: Style) {
        WidgetUpdateHelper.getPrefs(context).edit().putString(PREF_STYLE, style.id).apply()
        if (isEnabled(context)) show(context)
    }

    // --------------------------- Канал ---------------------------

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // тихое: без звука и вибрации
            ).apply {
                description = "Постоянная строка «сколько осталось до звонка»"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.createNotificationChannel(channel)
        }
    }

    // --------------------------- Показ ---------------------------

    /** Обновляет (или создаёт) постоянное уведомление. */
    fun show(context: Context) {
        if (!isEnabled(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        createChannel(context)

        val info = BellCountdownWidgetProvider.countdownInfo(context)
        val style = getStyle(context)

        // Тексты в зависимости от состояния дня
        val (title, text, progressPercent, hasProgress) = buildContent(info, style)

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mpk_emblem)
            .setContentIntent(openIntent)
            .setOngoing(true)                 // нельзя смахнуть
            .setOnlyAlertOnce(true)           // не пиликать при каждом обновлении
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setShowWhen(false)               // время приложения не при чём — не показываем

        when (style) {
            Style.COMPACT -> {
                builder.setContentTitle(title)
                if (text.isNotBlank()) builder.setContentText(text)
            }
            Style.WITH_APP_NAME -> {
                builder.setContentTitle("Мой Политех")
                builder.setContentText(if (text.isNotBlank()) "$title • $text" else title)
            }
            Style.WITH_LESSON -> {
                builder.setContentTitle(title)
                if (text.isNotBlank()) builder.setContentText(text)
                builder.setSubText(currentLessonLabel(info))
            }
            Style.WITH_PROGRESS -> {
                builder.setContentTitle(title)
                if (text.isNotBlank()) builder.setContentText(text)
                if (hasProgress) {
                    builder.setProgress(100, progressPercent, false)
                }
            }
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Разрешение отозвано — молча выходим
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Пересобирает уведомление после смены настроек/группы. */
    fun refresh(context: Context) {
        if (isEnabled(context)) show(context) else cancel(context)
    }

    // --------------------------- Тексты ---------------------------

    private data class Content(
        val title: String,
        val text: String,
        val progressPercent: Int,
        val hasProgress: Boolean
    )

    private fun buildContent(
        info: Triple<Long, String, Int>?,
        style: Style
    ): Content {
        if (info == null || info.first < 0) {
            return Content(
                title = "Уроки закончились",
                text = if (style == Style.WITH_PROGRESS) "Хорошего дня!" else "",
                progressPercent = 100,
                hasProgress = false
            )
        }

        val (minutesLeft, label, _) = info
        val value = BellCountdownWidgetProvider.formatMinutes(minutesLeft)

        // Прогресс внутри текущего интервала: для полосы нужны границы урока,
        // поэтому в этом стиле показываем долю от 45-минутного урока как ориентир.
        val progress = ((45 - minutesLeft).coerceIn(0, 45) * 100 / 45).toInt()

        return Content(
            title = label.replaceFirstChar { it.uppercase() } + ": $value",
            text = if (style == Style.WITH_PROGRESS) "Осталось $value" else "",
            progressPercent = progress,
            hasProgress = style == Style.WITH_PROGRESS
        )
    }

    /** Подпись под текстом в стиле WITH_LESSON: «3 урок» / «Перемена» / «1 урок». */
    private fun currentLessonLabel(info: Triple<Long, String, Int>?): String {
        val lessonNumber = info?.third ?: 0
        return if (lessonNumber > 0) "$lessonNumber урок" else "Перемена"
    }
}
