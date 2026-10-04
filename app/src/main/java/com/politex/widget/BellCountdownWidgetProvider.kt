package com.politex.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.politex.R
import com.politex.data.model.BellItem
import com.politex.data.model.CollegeBellSchedule
import com.politex.util.Dow
import kotlinx.coroutines.launch
import java.util.Calendar

class BellCountdownWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {

        val pending = goAsync()
        WidgetUpdateHelper.widgetScope.launch {
            try {
                updateWidgets(context, appWidgetManager, appWidgetIds)
                WidgetAlarm.scheduleNext(context)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        WidgetAlarm.scheduleNext(context)
    }

    override fun onDisabled(context: Context) {
        WidgetAlarm.cancel(context)
    }

    companion object {

        const val SHOW_FROM_MINUTE = 7 * 60

        fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
            ids.forEach { id ->
                val views = buildViews(context, id)
                manager.updateAppWidget(id, views)
            }
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BellCountdownWidgetProvider::class.java))
            if (ids.isNotEmpty()) updateWidgets(context, manager, ids)
        }

        fun updateSecondsWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BellCountdownWidgetProvider::class.java))
            val secondsIds = ids.filter { WidgetOptions.seconds(context, it) }.toIntArray()
            if (secondsIds.isNotEmpty()) updateWidgets(context, manager, secondsIds)
        }

        fun countdownInfo(
            context: Context,
            mode: CountdownMode = CountdownMode.GROUP_LESSONS,
        ): Triple<Long, String, Int>? {
            val cal = java.util.Calendar.getInstance()
            val dow = Dow.of(cal)
            val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val bells = CollegeBellSchedule.getBellsForDay(dow)

            val lastGroupLesson = if (mode == CountdownMode.GROUP_LESSONS) {
                lastGroupLessonForToday(context, cal)
            } else {
                null
            }

            return countdownForMinute(minutes, dow, bells, mode, lastGroupLesson)
                ?: tomorrowFirstLessonLabel(minutes, dow, bells, mode, lastGroupLesson)
        }

        fun tomorrowFirstLessonLabel(
            minutes: Int,
            dayOfWeek: Int,
            bells: List<BellItem>,
            mode: CountdownMode,
            lastGroupLesson: Int?,
        ): Triple<Long, String, Int>? {
            if (minutes < SHOW_FROM_MINUTE) return null

            if (dayOfWeek == 7) return nextBellsDayLabel(minutes, dayOfWeek)
            if (bells.isEmpty()) return null

            val lastEnd = if (mode == CountdownMode.GROUP_LESSONS) {
                lastGroupLesson ?: return null
                bells.lastOrNull { !it.isInfoHour && it.lessonNumber <= lastGroupLesson }?.endMinutes
            } else {
                bells.last().endMinutes
            } ?: return null
            if (minutes <= lastEnd) return null

            return nextBellsDayLabel(minutes, dayOfWeek)
        }

        private fun nextBellsDayLabel(
            minutes: Int,
            dayOfWeek: Int,
        ): Triple<Long, String, Int>? {
            var daysAhead = 1
            var dow = if (dayOfWeek == 7) 1 else dayOfWeek + 1
            if (dow == 7) {
                dow = 1
                daysAhead = 2
            }
            val first = CollegeBellSchedule.getBellsForDay(dow).firstOrNull { !it.isInfoHour }
                ?: return null
            val minutesToStart = (daysAhead * 24 * 60 - minutes) + first.startMinutes
            val label = if (daysAhead > 1 || dayOfWeek == 7) "до 1 урока в пн" else "до 1 урока завтра"
            return Triple(minutesToStart.toLong(), label, 0)
        }

        fun nightFirstLessonLabel(
            minutes: Int,
            dayOfWeek: Int,
            bells: List<BellItem>,
            mode: CountdownMode,
            lastGroupLesson: Int?,
        ): Triple<Long, String, Int>? {
            if (dayOfWeek == 7 || bells.isEmpty()) return null
            if (minutes >= SHOW_FROM_MINUTE) return null

            if (mode == CountdownMode.GROUP_LESSONS && lastGroupLesson == null) return null

            val first = bells.firstOrNull { !it.isInfoHour } ?: return null
            if (minutes >= first.startMinutes) return null
            return Triple((first.startMinutes - minutes).toLong(), "до 1 урока", 0)
        }

        fun countdownForMinute(
            minutes: Int,
            dayOfWeek: Int,
            bells: List<BellItem>,
            mode: CountdownMode,
            lastGroupLesson: Int?,
        ): Triple<Long, String, Int>? {
            if (dayOfWeek == 7 || bells.isEmpty()) return null
            if (minutes < SHOW_FROM_MINUTE) return null

            if (mode == CountdownMode.GROUP_LESSONS) {

                if (lastGroupLesson == null) return null
                val lastBell = bells.lastOrNull {
                    !it.isInfoHour && it.lessonNumber <= lastGroupLesson
                } ?: return null
                if (minutes > lastBell.endMinutes) return null
            }

            bells.forEachIndexed { index, bell ->
                if (minutes in bell.startMinutes..bell.endMinutes) {
                    val left = bell.endMinutes - minutes
                    return Triple(left.toLong(), "до звонка", if (bell.isInfoHour) 0 else bell.lessonNumber)
                }

                val breakEnd = bell.endMinutes + bell.breakAfterMinutes
                if (bell.breakAfterMinutes > 0 && minutes in bell.endMinutes..breakEnd) {
                    val next = bells.getOrNull(index + 1) ?: return null
                    val left = next.startMinutes - minutes
                    return Triple(
                        left.toLong(),
                        if (next.isInfoHour) "до инфочаса" else "до ${next.lessonNumber} урока",
                        0,
                    )
                }
            }

            val first = bells.firstOrNull() ?: return null
            if (minutes < first.startMinutes) {
                return Triple((first.startMinutes - minutes).toLong(), "до 1 урока", 0)
            }
            return null
        }

        @Volatile private var cacheKey: String? = null

        @Volatile private var cacheLastLesson: Int? = null

        fun lastGroupLessonForToday(context: Context, cal: Calendar): Int? {
            val group = WidgetUpdateHelper.getSelectedGroup(context)
            if (group.isBlank()) return null
            val dow = Dow.of(cal)

            val todayString = WidgetData.todayDateString()

            val key = "$group|$dow|$todayString"
            if (key == cacheKey) return cacheLastLesson
            val result = try {
                kotlinx.coroutines.runBlocking {
                    val lessons = com.politex.data.local.MpkDatabase.getInstance(context)
                        .lessonDao().getLessonsForDaySync(group, dow)
                    val forToday = lessons.filter {
                        it.dateString.isNotBlank() && WidgetData.normalizeDate(it.dateString) == todayString
                    }

                    if (forToday.isNotEmpty()) {
                        forToday.maxOf { it.lessonNumber }
                    } else {
                        lessons.filter { it.dateString.isBlank() }.maxOfOrNull { it.lessonNumber }
                    }
                }
            } catch (_: Exception) {
                null
            }

            cacheLastLesson = result
            cacheKey = key
            return result
        }

        private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_bell_countdown)

            val style = WidgetStyle.load(context, appWidgetId)
            views.setInt(R.id.widget_countdown_root, "setBackgroundColor", style.backgroundColor)
            views.setTextColor(R.id.tv_countdown_label, style.subColor)
            views.setTextColor(R.id.tv_countdown_value, style.mainColor)

            val mode = CountdownMode.load(context, appWidgetId)
            val cal = java.util.Calendar.getInstance()
            val dow = Dow.of(cal)
            val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val bells = CollegeBellSchedule.getBellsForDay(dow)

            val lastGroupLesson = if (mode == CountdownMode.GROUP_LESSONS && dow in 1..6) {
                lastGroupLessonForToday(context, cal)
            } else {
                null
            }

            val info = countdownForMinute(minutes, dow, bells, mode, lastGroupLesson)

            val secondsOn = WidgetOptions.seconds(context, appWidgetId)
            val secondNow = cal.get(Calendar.SECOND)
            fun valueText(minutesLeft: Long): String =
                if (secondsOn) formatSeconds(minutesLeft * 60 - secondNow) else formatMinutes(minutesLeft)
            when {
                info != null && info.first >= 0 -> {
                    val (minutesLeft, label, _) = info
                    views.setTextViewText(R.id.tv_countdown_label, label)
                    views.setTextViewText(R.id.tv_countdown_value, valueText(minutesLeft))
                }

                else -> {
                    val tomorrow = tomorrowFirstLessonLabel(minutes, dow, bells, mode, lastGroupLesson)

                    val night = if (tomorrow == null) {
                        nightFirstLessonLabel(minutes, dow, bells, mode, lastGroupLesson)
                    } else {
                        null
                    }
                    when {
                        tomorrow != null -> {
                            views.setTextViewText(R.id.tv_countdown_label, tomorrow.second)
                            views.setTextViewText(R.id.tv_countdown_value, formatHoursRounded(tomorrow.first))
                        }
                        night != null -> {
                            views.setTextViewText(R.id.tv_countdown_label, night.second)
                            views.setTextViewText(R.id.tv_countdown_value, valueText(night.first))
                        }

                        dow in 1..6 && mode == CountdownMode.GROUP_LESSONS && lastGroupLesson == null -> {
                            views.setTextViewText(R.id.tv_countdown_label, "Уроков нет")
                            views.setViewVisibility(R.id.tv_countdown_value, android.view.View.GONE)
                        }
                        dow in 1..6 && mode == CountdownMode.ALL_BELLS && bells.isEmpty() -> {
                            views.setTextViewText(R.id.tv_countdown_label, "Звонков сегодня нет")
                            views.setViewVisibility(R.id.tv_countdown_value, android.view.View.GONE)
                        }
                        else -> {

                            views.setTextViewText(R.id.tv_countdown_label, "")
                            views.setTextViewText(R.id.tv_countdown_value, "")
                        }
                    }
                }
            }

            val openIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                Intent(context, com.politex.MainActivity::class.java)

                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("EXTRA_OPEN_TAB", "BELLS")
                    .putExtra("EXTRA_SCROLL_TARGET", "CURRENT_BELL"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_countdown_root, openIntent)
            return views
        }

        fun formatMinutes(totalMinutes: Long): String {
            if (totalMinutes <= 0) return "меньше минуты"
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            return when {
                h == 0L -> "$m мин"
                m == 0L -> "$h ч"
                else -> "$h ч $m мин"
            }
        }

        fun formatHoursRounded(totalMinutes: Long): String {
            val hours = Math.round(totalMinutes / 60.0).toLong().coerceAtLeast(1L)
            return "$hours ч"
        }

        fun formatSeconds(totalSeconds: Long): String {
            val s = totalSeconds.coerceAtLeast(0L)
            val h = s / 3600
            val m = (s % 3600) / 60
            val sec = s % 60
            return if (h == 0L) {
                String.format(java.util.Locale.US, "%d:%02d", m, sec)
            } else {
                String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, sec)
            }
        }
    }
}

enum class CountdownMode(val title: String, val description: String) {
    GROUP_LESSONS(
        "До конца моих уроков",
        "Отсчёт заканчивается на последнем уроке вашей группы",
    ),
    ALL_BELLS(
        "До конца всех звонков",
        "Отсчёт идёт до последнего звонка в колледже",
    ),
    ;

    companion object {
        private const val PREF_PREFIX = "countdown_mode_"

        fun load(context: Context, appWidgetId: Int): CountdownMode {
            val name = WidgetUpdateHelper.getPrefs(context).getString(PREF_PREFIX + appWidgetId, null)
            return entries.firstOrNull { it.name == name } ?: GROUP_LESSONS
        }

        fun save(context: Context, appWidgetId: Int, mode: CountdownMode) {
            WidgetUpdateHelper.getPrefs(context)
                .edit()
                .putString(PREF_PREFIX + appWidgetId, mode.name)
                .apply()
        }
    }
}

enum class WidgetStyle(
    val backgroundColor: Int,
    val mainColor: Int,
    val subColor: Int,

    val highlightColor: Int,

    val pastColor: Int,
    val title: String,
) {
    LIGHT(
        0xFFFFFFFF.toInt(),
        0xFF111827.toInt(),
        0xFF6B7280.toInt(),
        0xFFE8F0FA.toInt(),
        0xFF6B7280.toInt(),
        "Белый",
    ),
    DARK(
        0xFF001737.toInt(),
        0xFFFFFFFF.toInt(),
        0xFFB9C6D8.toInt(),
        0xFF0B3564.toInt(),
        0xFF5A6B85.toInt(),
        "Тёмно-синий",
    ),
    BLUE(
        0xFF0B3564.toInt(),
        0xFFFFFFFF.toInt(),
        0xFFAFC6E4.toInt(),
        0xFF16457F.toInt(),
        0xFF6E86A6.toInt(),
        "Фирменный синий",
    ),
    SKY(
        0xFF0072CE.toInt(),
        0xFFFFFFFF.toInt(),
        0xFFD5E9FA.toInt(),
        0xFF0B3564.toInt(),
        0xFF8FB8DD.toInt(),
        "Яркий синий",
    ),
    ;

    companion object {
        private const val PREF_PREFIX = "widget_style_"

        fun load(context: Context, appWidgetId: Int): WidgetStyle {
            val name = WidgetUpdateHelper.getPrefs(context).getString(PREF_PREFIX + appWidgetId, null)
            return entries.firstOrNull { it.name == name } ?: LIGHT
        }

        fun save(context: Context, appWidgetId: Int, style: WidgetStyle) {
            WidgetUpdateHelper.getPrefs(context)
                .edit()
                .putString(PREF_PREFIX + appWidgetId, style.name)
                .apply()
        }
    }
}

object WidgetAlarm {

    private const val SCHOOL_START = 7 * 60
    private const val SCHOOL_END = 21 * 60

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetAlarmReceiver::class.java).apply {
            action = WidgetAlarmReceiver.ACTION_TICK
        }
        return PendingIntent.getBroadcast(
            context,
            4002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun hasWork(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return listOf(
            BellCountdownWidgetProvider::class.java,
            NowNextWidgetProvider::class.java,
            TodayScheduleWidgetProvider::class.java,
            BellsWidgetProvider::class.java,
        ).any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    }

    fun scheduleNext(context: Context) {
        if (!hasWork(context)) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance()
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val triggerAt = if (minutes in SCHOOL_START until SCHOOL_END) {
            System.currentTimeMillis() + 60_000L
        } else {

            (cal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 7)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis
        }

        val pi = pendingIntent(context)

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }
}

class WidgetAlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TICK -> {

                val pending = goAsync()
                WidgetUpdateHelper.widgetScope.launch {
                    try {

                        if (WidgetUpdateHelper.isFullUpdateStale(context)) {
                            WidgetUpdateHelper.updateAllWidgets(context)
                        } else {

                            val serviceAlive = com.politex.util.BellCountdownNotifier.isEnabled(context) &&
                                com.politex.util.BellTimerService.isRunning
                            if (serviceAlive) {
                                NowNextWidgetProvider.updateAll(context)
                            } else {
                                WidgetUpdateHelper.updateMinuteWidgets(context)
                            }
                        }
                        WidgetAlarm.scheduleNext(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                WidgetAlarm.scheduleNext(context)

                if (com.politex.util.BellCountdownNotifier.isEnabled(context)) {
                    com.politex.util.BellTimerService.start(context)
                }
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.politex.widget.ACTION_BELL_COUNTDOWN_TICK"
    }
}
