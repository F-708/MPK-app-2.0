package com.politex.util

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
import com.politex.util.Dow
import com.politex.MainActivity
import com.politex.R
import com.politex.data.model.CollegeBellSchedule
import com.politex.widget.BellCountdownWidgetProvider
import com.politex.widget.WidgetUpdateHelper
import java.util.Calendar

object BellCountdownNotifier {

    const val CHANNEL_ID = "mpk_bell_countdown"
    const val CHANNEL_NAME = "До звонка"

    const val NOTIFICATION_ID = 2027

    private const val PREF_ENABLED = "bell_notification_enabled"
    private const val PREF_STYLE = "bell_notification_style"
    private const val PREF_THEME = "bell_notification_theme"
    private const val PREF_SCOPE = "bell_notification_scope"
    private const val PREF_PINNED = "bell_notification_pinned"

    enum class Scope(val id: String, val title: String, val description: String) {
        MY_LESSONS("my_lessons", "Мои уроки", "Счётчик заканчивается с последней парой вашей группы"),
        COLLEGE("college", "Уроки колледжа", "Счётчик работает до последнего звонка колледжа");

        companion object {
            fun from(id: String?): Scope = entries.firstOrNull { it.id == id } ?: MY_LESSONS
        }
    }

    enum class Theme(val id: String, val title: String, val accent: Int, val colorize: Boolean) {
        SYSTEM("system", "Системная", 0, false),
        BRAND("brand", "Фирменный синий", 0xFF0B3564.toInt(), true),
        SKY("sky", "Яркий синий", 0xFF0072CE.toInt(), true),
        DARK("dark", "Тёмная", 0xFF232527.toInt(), true);

        companion object {
            fun from(id: String?): Theme = entries.firstOrNull { it.id == id } ?: SYSTEM
        }
    }

    enum class Style(val id: String, val title: String) {
        COMPACT("compact", "Компактный"),
        WITH_APP_NAME("app_name", "С названием приложения"),
        WITH_LESSON("lesson", "С номером урока"),
        WITH_PROGRESS("progress", "С полосой прогресса");

        companion object {
            fun from(id: String?): Style = entries.firstOrNull { it.id == id } ?: COMPACT
        }
    }

    fun isEnabled(context: Context): Boolean =
        WidgetUpdateHelper.getPrefs(context).getBoolean(PREF_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        WidgetUpdateHelper.getPrefs(context).edit().putBoolean(PREF_ENABLED, enabled).apply()
        if (enabled) {

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

    fun getScope(context: Context): Scope =
        Scope.from(WidgetUpdateHelper.getPrefs(context).getString(PREF_SCOPE, null))

    fun setScope(context: Context, scope: Scope) {
        WidgetUpdateHelper.getPrefs(context).edit().putString(PREF_SCOPE, scope.id).apply()
        if (isEnabled(context)) show(context)
    }

    fun isPinned(context: Context): Boolean =
        WidgetUpdateHelper.getPrefs(context).getBoolean(PREF_PINNED, true)

    fun setPinned(context: Context, pinned: Boolean) {
        WidgetUpdateHelper.getPrefs(context).edit().putBoolean(PREF_PINNED, pinned).apply()
        if (isEnabled(context)) show(context)
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
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

    fun show(context: Context) {
        if (!isEnabled(context)) return
        if (!canPost(context)) return
        val notification = buildNotification(context)
        // O6: в «мёртвом окне» (>конца уроков и до 06:00) тишина. Если сервис foreground —
        // уведомление показать обязан сам сервис (startForeground), поэтому здесь его не
        // трогаем: остаётся тихая заглушка (buildNotification). Если foreground-сервис не
        // активен (уведомление показано из настроек/after boot) — снимаем уведомление совсем.
        if (isQuietWindowNow(context) && !BellTimerService.isRunning) {
            cancel(context)
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {

        }
    }

    /** O6: возобновление показа утром — 06:00 (перебивает виджетный гейт 07:00). */
    const val RESUME_MINUTE = 6 * 60

    /**
     * O6: «мёртвое окно» — уроки сегодня закончились, но утро (06:00) ещё не наступило.
     * Тишина тянется с [lessonsEndedMinute] до 06:00 СЛЕДУЮЩЕГО дня, поэтому:
     *  - сейчас < 06:00 → тишина всегда (уроков в пятом часу нет);
     *  - сейчас в 06:00..[lessonsEndedMinute] → показываем (уроки идут);
     *  - сейчас > [lessonsEndedMinute] → тишина до утра.
     * [lessonsEndedMinute] == null (уроков в этот день нет) → тишина весь день. Чистая функция.
     */
    fun isQuietWindow(nowMinutes: Int, lessonsEndedMinute: Int?): Boolean {
        if (nowMinutes < RESUME_MINUTE) return true
        if (lessonsEndedMinute == null) return true
        return nowMinutes > lessonsEndedMinute
    }

    /**
     * O6: минута конца последних уроков по режиму (колледж — конец последнего звонка дня,
     * мои уроки — конец последнего урока группы). null — уроков в этот день нет.
     */
    private fun lessonsEndedMinuteToday(context: Context): Int? {
        val cal = Calendar.getInstance()
        val dow = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> return null
        }
        val bells = CollegeBellSchedule.getBellsForDay(dow)
        return when (getScope(context)) {
            Scope.COLLEGE -> bells.lastOrNull { !it.isInfoHour }?.endMinutes
            Scope.MY_LESSONS -> {
                val last = BellCountdownWidgetProvider.lastGroupLessonForToday(context, cal) ?: return null
                bells.lastOrNull { !it.isInfoHour && it.lessonNumber <= last }?.endMinutes
            }
        }
    }

    /**
     * O6: утреннее окно 06:00–07:00 — виджетный гейт 07:00 ещё скрывает строку, но заказчику
     * она нужна с 06:00. Показываем «до 1 урока», только если сегодня есть уроки
     * (нет уроков → null → тишина весь день). С 07:00 строку строит обычный путь.
     */
    private fun morningInfo(context: Context): Triple<Long, String, Int>? {
        val cal = Calendar.getInstance()
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        if (isQuietWindow(now, lessonsEndedMinuteToday(context))) return null

        val first = CollegeBellSchedule.getBellsForDay(modeLessonsDay(cal)).firstOrNull() ?: return null
        if (now >= first.startMinutes) return null
        return Triple((first.startMinutes - now).toLong(), "до 1 урока", 0)
    }

    /** День недели в формате расписания (1=Пн..6=Сб, 7=Вс). */
    private fun modeLessonsDay(cal: Calendar): Int = Dow.of(cal)

    private fun isQuietWindowNow(context: Context): Boolean {
        val cal = Calendar.getInstance()
        return isQuietWindow(
            cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE),
            lessonsEndedMinuteToday(context)
        )
    }

    fun buildNotification(context: Context): android.app.Notification {
        createChannel(context)

        val scope = getScope(context)
        val mode = when (scope) {
            Scope.MY_LESSONS -> com.politex.widget.CountdownMode.GROUP_LESSONS
            Scope.COLLEGE -> com.politex.widget.CountdownMode.ALL_BELLS
        }
        val info = BellCountdownWidgetProvider.countdownInfo(context, mode)

        // Т10: скрыто (выходной / до 07:00 / уроки закончились / нет уроков) —
        // минимальная тихая заглушка вместо «Уроки закончились» круглосуточно.
        if (info == null || info.first < 0) {
            // O6: 06:00–07:00 — виджетный гейт 07:00 (не наш файл) ещё молчит, но заказчику
            // строку надо вернуть уже в 06:00. Даём её здесь, если по режиму есть уроки сегодня.
            morningInfo(context)?.let { return buildVisibleNotification(context, it) }
            // N5: строка должна быть «живой» как в Яндекс.Картах — всегда показывать актуальное.
            // Вечер после уроков и ночь до уроков берём те же подписи, что и виджет
            // («до 1 урока завтра: 14 ч» / «до 1 урока: N мин»), вместо пустой заглушки.
            eveningOrNightInfo(context, mode)?.let { return buildVisibleNotification(context, it) }
            return buildHiddenNotification(context)
        }

        return buildVisibleNotification(context, info)
    }

    /**
     * N5: вечернее/ночное окно — та же логика, что у виджета (W3/O6), чтобы строка
     * уведомления никогда не была бессмысленной. null → заглушка (выходной/нет уроков).
     */
    private fun eveningOrNightInfo(
        context: Context,
        mode: com.politex.widget.CountdownMode
    ): Triple<Long, String, Int>? {
        val cal = Calendar.getInstance()
        val dow = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> return null
        }
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val bells = CollegeBellSchedule.getBellsForDay(dow)
        val lastGroupLesson = if (mode == com.politex.widget.CountdownMode.GROUP_LESSONS) {
            BellCountdownWidgetProvider.lastGroupLessonForToday(context, cal)
        } else null
        return BellCountdownWidgetProvider.tomorrowFirstLessonLabel(minutes, dow, bells, mode, lastGroupLesson)
            ?: BellCountdownWidgetProvider.nightFirstLessonLabel(minutes, dow, bells, mode, lastGroupLesson)
    }

    /** O6: сборка обычной строки отсчёта — общая для основного пути и утреннего окна 06:00. */
    private fun buildVisibleNotification(
        context: Context,
        info: Triple<Long, String, Int>
    ): android.app.Notification {
        val style = getStyle(context)

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
            .setOngoing(isPinned(context))
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setShowWhen(false)
            // N5: кастомный вид — крупные цифры отсчёта (как в Яндекс.Погоде), а не просто
            // заголовок+подпись. Decorated сохраняет системную шапку (иконка, время, приложение).
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())

        if (theme.accent != 0) {
            builder.color = theme.accent
            if (theme.colorize) {
                builder.setColorized(true)
            }
        }

        // Fallback для старых версий/свёрнутого вида — обычный текст
        builder.setContentTitle(title)
        builder.setContentText(info.second)

        // Кастомный RemoteViews: крупное значение + единица + подпись
        val views = android.widget.RemoteViews(context.packageName, R.layout.notification_bell_countdown)
        val valueText = BellCountdownWidgetProvider.formatMinutes(info.first)
        // «25 мин» → value=«25», unit=«мин»; «1 ч 5 мин» → value=«1 ч 5», unit=«мин» —
        // просто режем по последнему пробелу, цифры крупно, единица мелко
        val cut = valueText.lastIndexOf(' ')
        if (cut > 0) {
            views.setTextViewText(R.id.nbell_value, valueText.substring(0, cut))
            views.setTextViewText(R.id.nbell_unit, valueText.substring(cut + 1))
        } else {
            views.setTextViewText(R.id.nbell_value, valueText)
            views.setTextViewText(R.id.nbell_unit, "")
        }
        views.setTextViewText(R.id.nbell_label, info.second)
        views.setViewVisibility(
            R.id.nbell_app,
            if (style == Style.WITH_APP_NAME) android.view.View.VISIBLE else android.view.View.GONE
        )
        views.setViewVisibility(
            R.id.nbell_progress,
            if (style == Style.WITH_PROGRESS && hasProgress) android.view.View.VISIBLE else android.view.View.GONE
        )
        if (style == Style.WITH_PROGRESS && hasProgress) {
            views.setProgressBar(R.id.nbell_progress, 100, progressPercent, false)
        }
        if (style == Style.WITH_LESSON) {
            views.setTextViewText(R.id.nbell_label, "${info.second} • ${currentLessonLabel(info)}")
        }
        builder.setCustomContentView(views)

        return builder.build()
    }

    /**
     * Заглушка для скрытого состояния: служба foreground обязана держать уведомление,
     * поэтому «исчезновение» строки = замена на минимальную тихую (Т10.2).
     */
    private fun buildHiddenNotification(context: Context): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mpk_emblem)
            .setContentIntent(openIntent)
            .setContentTitle("Мой Политех")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setShowWhen(false)
            .build()
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun refresh(context: Context) {
        if (isEnabled(context)) show(context) else cancel(context)
    }

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

        val progress = ((45 - minutesLeft).coerceIn(0, 45) * 100 / 45).toInt()

        return Content(
            title = label.replaceFirstChar { it.uppercase() } + ": $value",
            progressPercent = progress,
            hasProgress = style == Style.WITH_PROGRESS
        )
    }

    private fun currentLessonLabel(info: Triple<Long, String, Int>?): String {
        val lessonNumber = info?.third ?: 0
        return if (lessonNumber > 0) "$lessonNumber урок" else "Перемена"
    }
}
