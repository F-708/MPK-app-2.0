package com.politex.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.politex.R
import com.politex.data.model.CollegeBellSchedule

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

            // W1: воскресенье — звонки понедельника БЕЗ подсветки «сейчас/прошло»
            // (ложный «идёт урок» в вс обесценивает доверие к виджету)
            val isSunday = dow == 7
            val bells = CollegeBellSchedule.getBellsForDay(if (isSunday) 1 else dow)

            if (isSunday) {
                views.setTextViewText(R.id.tv_bells_header, "ЗВОНКИ ПН")
            }

            views.removeAllViews(R.id.bells_rows)

            bells.forEach { bell ->
                val isCurrent = !isSunday && now in bell.startMinutes..bell.endMinutes
                val isPast = !isSunday && now > bell.endMinutes

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

                row.setTextViewText(R.id.row_number, if (bell.isInfoHour) "инф" else "${bell.lessonNumber}")
                row.setTextColor(R.id.row_number, textColor)

                row.setTextViewText(R.id.row_time, "${bell.start}–${bell.end}")
                row.setTextColor(R.id.row_time, if (isCurrent) style.mainColor else style.subColor)

                views.addView(R.id.bells_rows, row)
            }

            val open = PendingIntent.getActivity(
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
            views.setOnClickPendingIntent(R.id.bells_root, open)
            return views
        }
    }
}
