package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.R
import com.example.data.local.entity.LessonEntity

class TodayScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, id)) }
        WidgetAlarm.scheduleNext(context)
    }

    override fun onEnabled(context: Context) = WidgetAlarm.scheduleNext(context)

    companion object {

        private const val MAX_ROWS = 12

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayScheduleWidgetProvider::class.java))
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, id)) }
        }

        private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today_schedule)
            val style = WidgetStyle.load(context, appWidgetId)
            val showRoom = WidgetOptions.showRoom(context, appWidgetId)

            views.setInt(R.id.today_root, "setBackgroundColor", style.backgroundColor)
            views.setTextColor(R.id.tv_today_header, style.subColor)
            views.setTextColor(R.id.tv_today_empty, style.subColor)

            val now = WidgetData.minuteOfDay(context)
            val lessons = WidgetData.todayLessons(context)
            val group = WidgetUpdateHelper.getSelectedGroup(context)

            views.setTextViewText(
                R.id.tv_today_header,
                if (group.isBlank()) "РАСПИСАНИЕ" else "СЕГОДНЯ • $group"
            )

            views.removeAllViews(R.id.today_rows)

            if (lessons.isEmpty()) {
                views.setViewVisibility(R.id.tv_today_empty, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.tv_today_empty, android.view.View.GONE)
                lessons.take(MAX_ROWS).forEach { lesson ->
                    views.addView(R.id.today_rows, buildRow(context, lesson, now, style, showRoom))
                }
            }

            val open = PendingIntent.getActivity(
                context,
                appWidgetId,
                Intent(context, com.example.MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.today_root, open)
            return views
        }

        private fun buildRow(
            context: Context,
            lesson: LessonEntity,
            now: Int,
            style: WidgetStyle,
            showRoom: Boolean
        ): RemoteViews {
            val row = RemoteViews(context.packageName, R.layout.widget_row_list)

            val isCurrent = now in lesson.startMinutes()..lesson.endMinutes()
            val isPast = now > lesson.endMinutes()

            val textColor = when {
                isCurrent -> style.mainColor
                isPast -> style.pastColor
                else -> style.mainColor
            }

            row.setInt(
                R.id.row_root,
                "setBackgroundColor",
                if (isCurrent) style.highlightColor else style.backgroundColor
            )

            row.setTextViewText(R.id.row_number, "${lesson.lessonNumber}")
            row.setTextColor(R.id.row_number, textColor)

            row.setTextViewText(R.id.row_time, "${lesson.timeStart}–${lesson.timeEnd}")
            row.setTextColor(R.id.row_time, if (isCurrent) style.mainColor else style.subColor)

            row.setTextViewText(
                R.id.row_subject,
                com.example.util.SubjectFormatter.getShortName(lesson.subjectRaw)
            )
            row.setTextColor(R.id.row_subject, textColor)

            val room = lesson.roomFirst.takeIf { it.isNotBlank() } ?: lesson.roomSecond
            row.setTextViewText(
                R.id.row_room,
                if (showRoom && room.isNotBlank()) "каб. $room" else ""
            )
            row.setTextColor(R.id.row_room, style.subColor)

            return row
        }
    }
}
