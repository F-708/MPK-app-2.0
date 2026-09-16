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
 * Выключено по умолчанию. Минутный такт держит служба BellTimerService,
 * привязанный к границе минуты устройства (AlarmManager душится Doze).
 * Оформление: выбор стиля строки и цветовой темы в настройках.
 */
object BellCountdownNotifier {

    /** id канала уведомления (создаётся отдельно, низкий приоритет — без звука). */
    const val CHANNEL_ID = "mpk_bell_countdown"
    const val CHANNEL_NAME = "До звонка"

    /** Фиксированный id постоянного уведомления. */
    const val NOTIFICATION_ID = 2027

    private const val PREF_ENABLED = "bell_notification_enabled"
    private const val PREF_STYLE = "bell_notification_style"
    private const val PREF_THEME = "bell_notification_theme"

    /**
     * Цветовая тема строки: подбирается под оформление телефона.
     * @param accent цвет акцента; 0 = системный (не перекрашивать)
     * @param colorize заливать ли фон цветом (поддерживается не всеми оболочками)
     */
    enum class Theme(val id: String, val title: String, val accent: Int, val colorize: Boolean) {
        SYSTEM("system", "Системная", 0, false),
        BRAND("brand", "Фирменный синий", 0xFF0B3564.toInt(), true),
        SKY("sky", "Яркий синий", 0xFF0072CE.toInt(), true),
        DARK("dark", "Тёмная", 0xFF232527.toInt(), true);

        // Удалена «Золотая»: выглядела плохо. Сохранённая настройка "gold"
        // теперь молча откатывается к системной — см. from().
        companion object {
            fun from(id: String?): Theme = entries.firstOrNull { it.id == id } ?: SYSTEM
        }
    }

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
            // Минутный такт держит служба — она же показывает уведомление
            BellTimerService.start(context)
        } else {
            BellTimerService.stop(context)
            cancel(context)
        }
    }

    fun getStyle(context: Context): Style =
        Style.from(WidgetUpdateHelper.getPrefs(context).getString(PREF_STYLE, null))

    fun getTheme(context: Context): Theme =
        Theme.from(WidgetUpdateHelper.getPrefs(context).getString(PREF_THEME, null))

    fun setTheme(context: Context, theme: Theme) {
        WidgetUpdateHelper.getPrefs(context).edit().putString(PREF_THEME, theme.id).apply()
        if (isEnabled(context)) show(context)
    }

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
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, buildNotification(context))
        } catch (_: SecurityException) {
            // Разрешение отозвано — молча выходим
        }
    }

    /** Уведомление для запуска службы (startForeground требует готовый объект). */
    fun buildNotification(context: Context): android.app.Notification {
        createChannel(context)

        val info = BellCountdownWidgetProvider.countdownInfo(context)
        val style = getStyle(context)

        // Тексты в зависимости от состояния дня
        val (title, progressPercent, hasProgress) = buildContent(info, style)

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val theme = getTheme(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mpk_emblem)
            .setContentIntent(openIntent)
            .setOngoing(true)                 // нельзя смахнуть
            .setOnlyAlertOnce(true)           // не пиликать при каждом обновлении
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setShowWhen(false)               // время приложения не при чём — не показываем

        // Цветовая тема: акцент и (если поддерживается) заливка фона
        if (theme.accent != 0) {
            builder.color = theme.accent
            if (theme.colorize) {
                builder.setColorized(true)
            }
        }

        // Описание раньше дублировало заголовок («До звонка: 5 мин» / «Осталось 5 мин»),
        // поэтому текста нет ни в одном стиле — только заголовок и, где нужно, полоса.
        when (style) {
            Style.COMPACT -> {
                builder.setContentTitle(title)
            }
            Style.WITH_APP_NAME -> {
                builder.setContentTitle("Мой Политех")
                builder.setContentText(title)
            }
            Style.WITH_LESSON -> {
                builder.setContentTitle(title)
                builder.setSubText(currentLessonLabel(info))
            }
            Style.WITH_PROGRESS -> {
                builder.setContentTitle(title)
                if (hasProgress) {
                    builder.setProgress(100, progressPercent, false)
                }
            }
        }

        return builder.build()
    }

    /** Разрешены ли уведомления системой. */
    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

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
        val progressPercent: Int,
        val hasProgress: Boolean
    )

    private fun buildContent(
        info: Triple<Long, String, Int>?,
        style: Style
    ): Content {
        if (info == null || info.first < 0) {
            return Content(title = "Уроки закончились", progressPercent = 100, hasProgress = false)
        }

        val (minutesLeft, label, _) = info
        val value = BellCountdownWidgetProvider.formatMinutes(minutesLeft)

        // Прогресс внутри текущего интервала: для полосы нужны границы урока,
        // поэтому в этом стиле показываем долю от 45-минутного урока как ориентир.
        val progress = ((45 - minutesLeft).coerceIn(0, 45) * 100 / 45).toInt()

        return Content(
            title = label.replaceFirstChar { it.uppercase() } + ": $value",
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
