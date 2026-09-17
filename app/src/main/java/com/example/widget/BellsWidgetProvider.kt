package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.example.R
import com.example.data.model.CollegeBellSchedule

/**
 * Виджет «Звонки» — расписание звонков на сегодня с подсветкой текущего урока.
 *
 * В отличие от виджета расписания, который зависит от выбранной группы, этот
 * показывает общее расписание звонков колледжа: по нему ориентируются, когда
 * урока в базе нет, а понять, сколько идёт пара, всё равно нужно.
 */
class BellsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, id)) }
        WidgetAlarm.scheduleNext(context)
    }

    override fun onEnabled(context: Context) = WidgetAlarm.scheduleNext(context)

    companion object {

        private const val COLOR_CURRENT_BG = 0xFFE8F0FA.toInt()
        private const val COLOR_CURRENT_TEXT = 0xFF0B3564.toInt()
        private const val COLOR_PAST_TEXT = 0xFF9AA3AF.toInt()
        private const val COLOR_TEXT = 0xFF111827.toInt()
        private const val COLOR_SUB = 0xFF6B7280.toInt()
        private const val COLOR_TRANSPARENT = Color.TRANSPARENT

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BellsWidgetProvider::class.java))
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, id)) }
        }

        private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_bells)

            val now = WidgetData.minuteOfDay(context)
            val dow = WidgetData.dayOfWeek(context)
            // В воскресенье занятий нет — показываем понедельничные звонки,
            // иначе виджет стоял бы пустым весь выходной
            val bells = CollegeBellSchedule.getBellsForDay(if (dow == 7) 1 else dow)

            views.removeAllViews(R.id.bells_rows)

            bells.forEach { bell ->
                val isCurrent = now in bell.startMinutes..bell.endMinutes
                val isPast = now > bell.endMinutes

                val row = RemoteViews(context.packageName, R.layout.widget_row_list)
                row.setInt(
                    R.id.row_root,
                    "setBackgroundColor",
                    if (isCurrent) COLOR_CURRENT_BG else COLOR_TRANSPARENT
                )

                val label = if (bell.isInfoHour) "инфочас" else "${bell.lessonNumber}"
                val textColor = when {
                    isCurrent -> COLOR_CURRENT_TEXT
                    isPast -> COLOR_PAST_TEXT
                    else -> COLOR_TEXT
                }

                row.setTextViewText(R.id.row_number, label)
                row.setTextColor(R.id.row_number, textColor)

                row.setTextViewText(R.id.row_time, "${bell.start}–${bell.end}")
                row.setTextColor(R.id.row_time, if (isCurrent) COLOR_CURRENT_TEXT else COLOR_SUB)

                row.setTextViewText(R.id.row_subject, bell.title)
                row.setTextColor(R.id.row_subject, textColor)

                // В этом виджете кабинет не нужен — место отдано времени
                row.setTextViewText(R.id.row_room, "")

                views.addView(R.id.bells_rows, row)
            }

            val open = PendingIntent.getActivity(
                context,
                appWidgetId,
                Intent(context, com.example.MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.bells_root, open)
            return views
        }
    }
}
