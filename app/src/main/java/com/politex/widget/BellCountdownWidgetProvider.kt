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
import java.util.Calendar

class BellCountdownWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
        WidgetAlarm.scheduleNext(context)
    }

    override fun onEnabled(context: Context) {
        WidgetAlarm.scheduleNext(context)
    }

    override fun onDisabled(context: Context) {
        WidgetAlarm.cancel(context)
    }

    companion object {

        /** Первый показ строки/виджета — 07:00 (заказчик, Q5). */
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

        fun countdownInfo(
            context: Context,
            mode: CountdownMode = CountdownMode.GROUP_LESSONS
        ): Triple<Long, String, Int>? {
            val cal = java.util.Calendar.getInstance()
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

            // Режим «мои уроки»: cutoff = последний урок группы (уроки закончились → строка скрыта).
            // Данные считаем здесь и в цикле не матчим звонки после последнего урока группы.
            val lastGroupLesson = if (mode == CountdownMode.GROUP_LESSONS) {
                lastGroupLessonForToday(context, cal)
            } else null

            return countdownForMinute(minutes, dow, bells, mode, lastGroupLesson)
        }

        /**
         * Чистая логика отсчёта (Т10): null = строка скрыта
         * (выходной / до 07:00 / уроки закончились / у группы нет уроков в этот день).
         *
         * Cutoff группы проверяется ДО цикла по звонкам (Т10.1) — сам цикл идёт по полному
         * списку, чтобы не ломать смежность «перемена → инфочас → следующий урок» (четверг).
         */
        fun countdownForMinute(
            minutes: Int,
            dayOfWeek: Int,
            bells: List<BellItem>,
            mode: CountdownMode,
            lastGroupLesson: Int?
        ): Triple<Long, String, Int>? {
            if (dayOfWeek == 7 || bells.isEmpty()) return null
            if (minutes < SHOW_FROM_MINUTE) return null

            if (mode == CountdownMode.GROUP_LESSONS) {
                // Нет уроков группы в этот день — скрыто весь день (без fallback на все звонки).
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
                        0
                    )
                }
            }

            val first = bells.firstOrNull() ?: return null
            if (minutes < first.startMinutes) {
                return Triple((first.startMinutes - minutes).toLong(), "до 1 урока", 0)
            }
            return null
        }

        /**
         * Последний урок группы сегодня: сначала строки с датой (dateString dd.MM.yyyy / dd-MM-yyyy),
         * при отсутствии — регулярные по dayOfWeek (graceful degradation). Пусто → null (уроков нет).
         */
        fun lastGroupLessonForToday(context: Context, cal: Calendar): Int? {
            val group = WidgetUpdateHelper.getSelectedGroup(context)
            if (group.isBlank()) return null
            val dow = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
                else -> 7
            }
            val todayString = String.format(
                "%02d.%02d.%d",
                cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR)
            )
            return try {
                kotlinx.coroutines.runBlocking {
                    val lessons = com.politex.data.local.MpkDatabase.getInstance(context)
                        .lessonDao().getLessonsForDaySync(group, dow)
                    val forToday = lessons.filter {
                        it.dateString.isNotBlank() && it.dateString.replace("-", ".") == todayString
                    }
                    // Дневные строки на сегодняшнюю дату имеют приоритет; иначе — регулярные по дню недели.
                    if (forToday.isNotEmpty()) forToday.maxOf { it.lessonNumber }
                    else lessons.filter { it.dateString.isBlank() }.maxOfOrNull { it.lessonNumber }
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_bell_countdown)

            val style = WidgetStyle.load(context, appWidgetId)
            views.setInt(R.id.widget_countdown_root, "setBackgroundColor", style.backgroundColor)
            views.setTextColor(R.id.tv_countdown_label, style.subColor)
            views.setTextColor(R.id.tv_countdown_value, style.mainColor)

            val info = countdownInfo(context, CountdownMode.load(context, appWidgetId))
            when {
                info == null || info.first < 0 -> {
                    // Т10: строка скрыта до 07:00 / после конца уроков / в день без уроков.
                    views.setTextViewText(R.id.tv_countdown_label, "")
                    views.setTextViewText(R.id.tv_countdown_value, "")
                }
                else -> {
                    val (minutesLeft, label, _) = info
                    views.setTextViewText(R.id.tv_countdown_label, label)
                    views.setTextViewText(R.id.tv_countdown_value, formatMinutes(minutesLeft))
                }
            }

            val openIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                Intent(context, com.politex.MainActivity::class.java)
                    // CLEAR_TOP|SINGLE_TOP: тёплый тап по виджету доходит до onNewIntent,
                    // а не плодит копии активити
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("EXTRA_OPEN_TAB", "BELLS")
                    .putExtra("EXTRA_SCROLL_TARGET", "CURRENT_BELL"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_countdown_root, openIntent)
            WidgetConfetti.applyFrame(views)
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
    }
}

enum class CountdownMode(val title: String, val description: String) {
    GROUP_LESSONS(
        "До конца моих уроков",
        "Отсчёт заканчивается на последнем уроке вашей группы"
    ),
    ALL_BELLS(
        "До конца всех звонков",
        "Отсчёт идёт до последнего звонка в колледже"
    );

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

        fun remove(context: Context, appWidgetId: Int) {
            WidgetUpdateHelper.getPrefs(context).edit().remove(PREF_PREFIX + appWidgetId).apply()
        }
    }
}

enum class WidgetStyle(
    val backgroundColor: Int,
    val mainColor: Int,
    val subColor: Int,

    val highlightColor: Int,

    val pastColor: Int,
    val title: String
) {
    LIGHT(
        0xFFFFFFFF.toInt(), 0xFF111827.toInt(), 0xFF6B7280.toInt(),
        0xFFE8F0FA.toInt(), 0xFF9AA3AF.toInt(), "Белый"
    ),
    DARK(
        0xFF001737.toInt(), 0xFFFFFFFF.toInt(), 0xFFB9C6D8.toInt(),
        0xFF0B3564.toInt(), 0xFF5A6B85.toInt(), "Тёмно-синий"
    ),
    BLUE(
        0xFF0B3564.toInt(), 0xFFFFFFFF.toInt(), 0xFFAFC6E4.toInt(),
        0xFF16457F.toInt(), 0xFF6E86A6.toInt(), "Фирменный синий"
    ),
    SKY(
        0xFF0072CE.toInt(), 0xFFFFFFFF.toInt(), 0xFFD5E9FA.toInt(),
        0xFF0B3564.toInt(), 0xFF8FB8DD.toInt(), "Яркий синий"
    );

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

        fun remove(context: Context, appWidgetId: Int) {
            WidgetUpdateHelper.getPrefs(context).edit().remove(PREF_PREFIX + appWidgetId).apply()
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
            context, 4002, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun hasWork(context: Context): Boolean {
        if (com.politex.util.BellCountdownNotifier.isEnabled(context)) return true
        val manager = AppWidgetManager.getInstance(context)
        val anyWidget = listOf(
            BellCountdownWidgetProvider::class.java,
            NowNextWidgetProvider::class.java,
            TodayScheduleWidgetProvider::class.java,
            BellsWidgetProvider::class.java
        ).any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
        return anyWidget
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
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 7)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis
        }

        val pi = pendingIntent(context)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
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

                WidgetUpdateHelper.updateAllWidgets(context)
                com.politex.util.ConfettiController.maybeCelebrate(context)
                WidgetAlarm.scheduleNext(context)
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
