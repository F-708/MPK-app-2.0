package com.example.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.example.R
import com.example.data.model.CollegeBellSchedule
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

            if (mode == CountdownMode.GROUP_LESSONS) {
                val group = WidgetUpdateHelper.getSelectedGroup(context)
                val lastGroupLesson = try {
                    kotlinx.coroutines.runBlocking {
                        val dow = when (cal.get(Calendar.DAY_OF_WEEK)) {
                            Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
                            Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
                            else -> 7
                        }
                        val lessons = com.example.data.local.MpkDatabase.getInstance(context)
                            .lessonDao().getLessonsForDaySync(group, dow)
                        val latestDate = lessons.filter { it.dateString.isNotBlank() }
                            .maxOfOrNull { it.dateString.replace("-", ".") }
                        val today = if (latestDate != null) {
                            lessons.filter { it.dateString.isBlank() || it.dateString.replace("-", ".") == latestDate }
                        } else lessons
                        today.maxOfOrNull { it.lessonNumber }
                    }
                } catch (_: Exception) {
                    null
                }
                if (lastGroupLesson != null) {
                    val lastBell = bells.lastOrNull { !it.isInfoHour && it.lessonNumber <= lastGroupLesson }
                    if (lastBell != null && minutes > lastBell.endMinutes) {
                        return Triple(-1L, "уроки закончились", 0)
                    }
                }
            }
            return null
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
                    views.setTextViewText(R.id.tv_countdown_label, "Уроки")
                    views.setTextViewText(R.id.tv_countdown_value, "закончились")
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
                Intent(context, com.example.MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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
        if (com.example.util.BellCountdownNotifier.isEnabled(context)) return true
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
                WidgetAlarm.scheduleNext(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                WidgetAlarm.scheduleNext(context)

                if (com.example.util.BellCountdownNotifier.isEnabled(context)) {
                    com.example.util.BellTimerService.start(context)
                }
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.example.widget.ACTION_BELL_COUNTDOWN_TICK"
    }
}
