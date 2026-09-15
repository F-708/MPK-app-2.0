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
import java.util.Calendar

/**
 * Виджет «Бегущая строка» — вертикальная шкала учебного дня.
 *
 * Узкий (1 клетка в ширину): линия с рисками и точкой текущего времени.
 * Широкий (2 клетки и больше, растягивается по горизонтали): плюс подписи
 * номеров уроков и текущее время.
 *
 * Обновление: каждую минуту через AlarmManager, но только в учебное время
 * (07:00–21:00) — вне его и ночью будильник спит, батарея не расходуется.
 */
open class TimelineWidgetProvider(
    private val horizontal: Boolean = false
) : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds, horizontal)
        TimelineAlarm.scheduleNext(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId), horizontal)
    }

    override fun onEnabled(context: Context) {
        TimelineAlarm.scheduleNext(context)
    }

    override fun onDisabled(context: Context) {
        if (noWidgetsLeft(context)) {
            TimelineAlarm.cancel(context)
        }
    }

    companion object {
        fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            horizontal: Boolean
        ) {
            val density = context.resources.displayMetrics.density
            appWidgetIds.forEach { id ->
                val options = appWidgetManager.getAppWidgetOptions(id)
                // Портретная ориентация: минимальная ширина x максимальная высота
                val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 70)
                val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 250)
                val widthPx = (widthDp * density).toInt().coerceAtLeast(24)
                val heightPx = (heightDp * density).toInt().coerceAtLeast(24)

                val views = RemoteViews(context.packageName, R.layout.widget_timeline).apply {
                    setImageViewBitmap(
                        R.id.iv_timeline,
                        TimelineDrawer.draw(context, widthPx, heightPx, horizontal, density)
                    )
                }
                // Клик — открыть приложение
                val openIntent = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, com.example.MainActivity::class.java).apply {
                        putExtra("EXTRA_OPEN_TAB", "SCHEDULE")
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_timeline_root, openIntent)
                appWidgetManager.updateAppWidget(id, views)
            }
        }

        /** Обновить все таймлайн-виджеты (вертикальные и горизонтальные). */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val verticalIds = manager.getAppWidgetIds(ComponentName(context, VerticalTimelineWidgetProvider::class.java))
            if (verticalIds.isNotEmpty()) {
                updateWidgets(context, manager, verticalIds, horizontal = false)
            }
            val horizontalIds = manager.getAppWidgetIds(ComponentName(context, HorizontalTimelineWidgetProvider::class.java))
            if (horizontalIds.isNotEmpty()) {
                updateWidgets(context, manager, horizontalIds, horizontal = true)
            }
        }

        private fun noWidgetsLeft(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            val v = manager.getAppWidgetIds(ComponentName(context, VerticalTimelineWidgetProvider::class.java))
            val h = manager.getAppWidgetIds(ComponentName(context, HorizontalTimelineWidgetProvider::class.java))
            return v.isEmpty() && h.isEmpty()
        }
    }
}

/** Вертикальная бегущая строка: 1 клетка в ширину, 3–6 в высоту (растягивается). */
class VerticalTimelineWidgetProvider : TimelineWidgetProvider(horizontal = false)

/** Горизонтальная бегущая строка: 1 клетка в высоту, 3–6 в ширину. */
class HorizontalTimelineWidgetProvider : TimelineWidgetProvider(horizontal = true)

/**
 * Минутный тик таймлайна: AlarmManager с точным будильником в учебное время,
 * вне учебного времени — один будильник на следующий учебный день.
 */
object TimelineAlarm {

    private const val SCHOOL_START_MINUTES = 7 * 60      // 07:00
    private const val SCHOOL_END_MINUTES = 21 * 60       // 21:00
    private const val MINUTE_MS = 60_000L

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TimelineAlarmReceiver::class.java).apply {
            action = TimelineAlarmReceiver.ACTION_TIMELINE_TICK
        }
        return PendingIntent.getBroadcast(
            context, 4001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleNext(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val triggerAt: Long = if (nowMinutes in SCHOOL_START_MINUTES until SCHOOL_END_MINUTES) {
            // Следующая минута
            System.currentTimeMillis() + MINUTE_MS
        } else {
            // Следующий учебный день 07:00
            val next = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 7)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            next.timeInMillis
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

/** Приёмник минутного тика таймлайна и перезапуск будильника после перезагрузки. */
class TimelineAlarmReceiver : android.content.BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TIMELINE_TICK -> {
                TimelineWidgetProvider.updateAll(context)
                TimelineAlarm.scheduleNext(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                TimelineAlarm.scheduleNext(context)
            }
        }
    }

    companion object {
        const val ACTION_TIMELINE_TICK = "com.example.widget.ACTION_TIMELINE_TICK"
    }
}
