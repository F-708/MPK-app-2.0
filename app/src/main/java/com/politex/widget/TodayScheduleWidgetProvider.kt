package com.politex.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.politex.R
import com.politex.data.local.entity.LessonEntity
import kotlinx.coroutines.launch

class TodayScheduleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {

        val pending = goAsync()
        WidgetUpdateHelper.widgetScope.launch {
            try {
                ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, id)) }
                WidgetAlarm.scheduleNext(context)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onEnabled(context: Context) = WidgetAlarm.scheduleNext(context)

    companion object {
        private const val MAX_ROWS = 12

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayScheduleWidgetProvider::class.java))
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, id)) }
        }

        private fun buildViews(
            context: Context,
            appWidgetId: Int,
        ): RemoteViews {
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
                if (group.isBlank()) "РАСПИСАНИЕ" else "СЕГОДНЯ • $group",
            )

            views.setTextViewText(
                R.id.tv_today_empty,
                if (group.isBlank()) "Выберите группу в приложении" else "На сегодня уроков нет",
            )

            views.removeAllViews(R.id.today_rows)

            if (lessons.isEmpty()) {
                views.setViewVisibility(R.id.tv_today_empty, android.view.View.VISIBLE)
                // CC9: idle-анимация в пустом состоянии (AVD автостартует в ImageView, API24+)
                views.setImageViewResource(R.id.iv_idle, R.drawable.anim_idle)
                views.setViewVisibility(R.id.iv_idle, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.tv_today_empty, android.view.View.GONE)
                views.setViewVisibility(R.id.iv_idle, android.view.View.GONE)
                lessons.take(MAX_ROWS).forEach { lesson ->
                    views.addView(
                        R.id.today_rows,
                        buildRow(context, lesson, now, style, showRoom, appWidgetId),
                    )
                }

                if (lessons.size > MAX_ROWS) {
                    views.addView(
                        R.id.today_rows,
                        buildMoreRow(context, style, lessons.size - MAX_ROWS),
                    )
                }
            }

            val open =
                PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    Intent(context, com.politex.MainActivity::class.java)

                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra("EXTRA_OPEN_TAB", "SCHEDULE")
                        .putExtra("EXTRA_SCROLL_TARGET", "CURRENT_LESSON"),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            views.setOnClickPendingIntent(R.id.today_root, open)
            return views
        }

        private fun buildRow(
            context: Context,
            lesson: LessonEntity,
            now: Int,
            style: WidgetStyle,
            showRoom: Boolean,
            appWidgetId: Int,
        ): RemoteViews {
            val row = RemoteViews(context.packageName, R.layout.widget_row_list)

            val isCurrent = now in lesson.startMinutes()..lesson.endMinutes()
            val isPast = now > lesson.endMinutes()

            val textColor =
                when {
                    isCurrent -> style.mainColor
                    isPast -> style.pastColor
                    else -> style.mainColor
                }

            row.setInt(
                R.id.row_root,
                "setBackgroundColor",
                if (isCurrent) style.highlightColor else style.backgroundColor,
            )

            row.setTextViewText(R.id.row_number, "${lesson.lessonNumber}")
            row.setTextColor(R.id.row_number, textColor)

            row.setTextViewText(R.id.row_time, "${lesson.timeStart}–${lesson.timeEnd}")
            row.setTextColor(R.id.row_time, if (isCurrent) style.mainColor else style.subColor)

            row.setTextViewText(
                R.id.row_subject,
                com.politex.util.SubjectFormatter
                    .getShortName(lesson.subjectRaw),
            )
            row.setTextColor(R.id.row_subject, textColor)

            val room = lesson.roomFirst.takeIf { it.isNotBlank() } ?: lesson.roomSecond
            if (showRoom && room.isNotBlank()) {
                row.setTextViewText(R.id.row_room, "каб. $room")

                row.setTextColor(R.id.row_room, style.mainColor)
                row.setInt(R.id.row_room, "setBackgroundColor", style.highlightColor)
                val d = context.resources.displayMetrics.density

                val ph = (10 * d + 0.5f).toInt()
                val pv = (10 * d + 0.5f).toInt()
                row.setViewPadding(R.id.row_room, ph, pv, ph, pv)
                val intent =
                    Intent(context, com.politex.MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra("EXTRA_OPEN_TAB", "MAP")
                        .putExtra("EXTRA_ROOM", room)

                        .putExtra("EXTRA_SHOW_ROUTE", true)
                row.setOnClickPendingIntent(
                    R.id.row_room,
                    PendingIntent.getActivity(
                        context,
                        appWidgetId * 100 + lesson.lessonNumber,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            } else {
                row.setTextViewText(R.id.row_room, "")
                row.setTextColor(R.id.row_room, style.subColor)
            }

            return row
        }

        private fun buildMoreRow(
            context: Context,
            style: WidgetStyle,
            hiddenCount: Int,
        ): RemoteViews {
            val row = RemoteViews(context.packageName, R.layout.widget_row_list)
            row.setInt(R.id.row_root, "setBackgroundColor", style.backgroundColor)
            row.setTextViewText(R.id.row_number, "")
            row.setTextViewText(R.id.row_time, "")
            row.setTextViewText(R.id.row_subject, "…ещё $hiddenCount")
            row.setTextColor(R.id.row_subject, style.subColor)
            row.setTextViewText(R.id.row_room, "")
            return row
        }
    }
}
