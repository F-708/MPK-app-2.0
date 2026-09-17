package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.R
import com.example.data.model.CollegeBellSchedule

/**
 * Виджет «Звонки» — расписание звонков колледжа с подсветкой текущего урока.
 *
 * Строка максимально короткая: слева номер урока, справа время. Слова
 * «1 урок» рядом с цифрой «1» были лишними — номер урока и так понятен,
 * а место в виджете дорогое. Для инфочаса вместо номера — «инф».
 *
 * В отличие от виджета расписания, который зависит от выбранной группы, этот
 * показывает общее расписание звонков: по нему ориентируются, когда урока
 * в базе нет, а понять, сколько идёт пара, всё равно нужно.
 */
class BellsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, id)) }
        WidgetAlarm.scheduleNext(context)
    }

    override fun onEnabled(context: Context) = WidgetAlarm.scheduleNext(context)

    companion object {

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BellsWidgetProvider::class.java))
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, id)) }
        }

        private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_bells)
            val style = WidgetStyle.load(context, appWidgetId)

            views.setInt(R.id.bells_root, "setBackgroundColor", style.backgroundColor)
            views.setTextColor(R.id.tv_bells_header, style.subColor)

            val now = WidgetData.minuteOfDay(context)
            val dow = WidgetData.dayOfWeek(context)
            // В воскресенье занятий нет — показываем понедельничные звонки,
            // иначе виджет стоял бы пустым весь выходной
            val bells = CollegeBellSchedule.getBellsForDay(if (dow == 7) 1 else dow)

            views.removeAllViews(R.id.bells_rows)

            bells.forEach { bell ->
                val isCurrent = now in bell.startMinutes..bell.endMinutes
                val isPast = now > bell.endMinutes

                val row = RemoteViews(context.packageName, R.layout.widget_row_bell)
                row.setInt(
                    R.id.row_root,
                    "setBackgroundColor",
                    if (isCurrent) style.highlightColor else style.backgroundColor
                )

                val textColor = when {
                    isCurrent -> style.mainColor
                    isPast -> style.pastColor
                    else -> style.mainColor
                }

                // Инфочас — не урок, у него нет номера
                row.setTextViewText(R.id.row_number, if (bell.isInfoHour) "инф" else "${bell.lessonNumber}")
                row.setTextColor(R.id.row_number, textColor)

                row.setTextViewText(R.id.row_time, "${bell.start}–${bell.end}")
                row.setTextColor(R.id.row_time, if (isCurrent) style.mainColor else style.subColor)

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
