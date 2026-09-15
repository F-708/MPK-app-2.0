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

/**
 * Единственный виджет приложения «Мой Политех» — «До звонка».
 *
 * Показывает обратный отсчёт до ближайшего звонка (конца текущего урока
 * или начала следующего) и текущее время. Размер 2x1, растягивается по
 * горизонтали до 3x1. Цвет фона настраивается при добавлении.
 *
 * Обновление — каждую минуту в учебное время (07:00–21:00) через
 * AlarmManager; ночью будильник спит.
 */
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

        /** Данные обратного отсчёта с учётом дебаг-времени и факта уроков группы. */
        fun countdownInfo(context: Context): Triple<Long, String, Int>? {
            val cal = com.example.util.DebugClock.now(context)
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
                // Идёт урок: считаем до звонка с урока
                if (minutes in bell.startMinutes..bell.endMinutes) {
                    val left = bell.endMinutes - minutes
                    return Triple(left.toLong(), "до звонка", if (bell.isInfoHour) 0 else bell.lessonNumber)
                }
                // Перемена: до начала следующего урока
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

            // До начала дня
            val first = bells.firstOrNull() ?: return null
            if (minutes < first.startMinutes) {
                return Triple((first.startMinutes - minutes).toLong(), "до 1 урока", 0)
            }
            // Уроки закончились: по звонкам ИЛИ по факту уроков группы
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
            return null
        }

        private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_bell_countdown)

            val style = WidgetStyle.load(context, appWidgetId)
            views.setInt(R.id.widget_countdown_root, "setBackgroundColor", style.backgroundColor)
            val mainColor = style.mainColor
            val subColor = style.subColor
            views.setTextColor(R.id.tv_countdown, mainColor)
            views.setTextColor(R.id.tv_countdown_label, subColor)
            views.setTextColor(R.id.tv_countdown_clock, subColor)
            views.setTextColor(R.id.tv_countdown_lesson, subColor)

            val info = countdownInfo(context)
            if (info != null) {
                val (minutesLeft, label, lessonNum) = info
                if (minutesLeft < 0) {
                    // Уроки группы закончились
                    views.setTextViewText(R.id.tv_countdown, "Уроки")
                    views.setTextViewText(R.id.tv_countdown_label, "закончились")
                } else {
                    val h = minutesLeft / 60
                    val m = minutesLeft % 60
                    views.setTextViewText(
                        R.id.tv_countdown,
                        if (h > 0) String.format("%d:%02d", h, m) else String.format("%d", m)
                    )
                    views.setTextViewText(R.id.tv_countdown_label, if (h > 0) label else "$label, мин")
                }
            } else {
                views.setTextViewText(R.id.tv_countdown, "—")
                views.setTextViewText(R.id.tv_countdown_label, "уроки закончились")
            }
            views.setTextViewText(R.id.tv_countdown_lesson, "")

            // Клик — открыть приложение
            val openIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                Intent(context, com.example.MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_countdown_root, openIntent)
            return views
        }
    }
}

/** Стиль виджета: цвет фона и текста, настраивается при добавлении. */
enum class WidgetStyle(val backgroundColor: Int, val mainColor: Int, val subColor: Int, val title: String) {
    LIGHT(0xFFFFFFFF.toInt(), 0xFF0B3564.toInt(), 0xFF6B7280.toInt(), "Белый"),
    DARK(0xFF001737.toInt(), 0xFFFFFFFF.toInt(), 0xFFB9C6D8.toInt(), "Тёмно-синий"),
    BLUE(0xFF0B3564.toInt(), 0xFFFFFFFF.toInt(), 0xFFAFC6E4.toInt(), "Фирменный синий"),
    SKY(0xFF0072CE.toInt(), 0xFFFFFFFF.toInt(), 0xFFD5E9FA.toInt(), "Яркий синий");

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

/** Минутный тик виджета в учебное время + перезапуск после перезагрузки. */
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

    fun scheduleNext(context: Context) {
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
                BellCountdownWidgetProvider.updateAll(context)
                WidgetAlarm.scheduleNext(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> WidgetAlarm.scheduleNext(context)
        }
    }

    companion object {
        const val ACTION_TICK = "com.example.widget.ACTION_BELL_COUNTDOWN_TICK"
    }
}
