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
import com.example.data.local.entity.LessonEntity

/**
 * Виджет «Расписание на сегодня» — большая вертикальная версия.
 *
 * Весь день списком: номер, время, предмет, кабинет. Текущий урок подсвечен,
 * прошедшие приглушены — по виджету видно, где ты находишься, не открывая приложение.
 *
 * Строки добавляются в LinearLayout через addView: так один и тот же макет строки
 * переиспользуется для любого количества уроков, без отдельного RemoteViewsService.
 */
class TodayScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, id)) }
        WidgetAlarm.scheduleNext(context)
    }

    override fun onEnabled(context: Context) = WidgetAlarm.scheduleNext(context)

    companion object {

        /** Больше этого числа строк виджет не покажет — дальше он бесполезен на экране. */
        private const val MAX_ROWS = 9

        private const val COLOR_CURRENT_BG = 0xFFE8F0FA.toInt()
        private const val COLOR_CURRENT_TEXT = 0xFF0B3564.toInt()
        private const val COLOR_PAST_TEXT = 0xFF9AA3AF.toInt()
        private const val COLOR_TEXT = 0xFF111827.toInt()
        private const val COLOR_SUB = 0xFF6B7280.toInt()
        private const val COLOR_TRANSPARENT = Color.TRANSPARENT

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayScheduleWidgetProvider::class.java))
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, id)) }
        }

        private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today_schedule)
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
                    views.addView(R.id.today_rows, buildRow(context, lesson, now))
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

        private fun buildRow(context: Context, lesson: LessonEntity, now: Int): RemoteViews {
            val row = RemoteViews(context.packageName, R.layout.widget_row_list)

            val isCurrent = now in lesson.startMinutes()..lesson.endMinutes()
            val isPast = now > lesson.endMinutes()

            val textColor = when {
                isCurrent -> COLOR_CURRENT_TEXT
                isPast -> COLOR_PAST_TEXT
                else -> COLOR_TEXT
            }

            row.setInt(R.id.row_root, "setBackgroundColor", if (isCurrent) COLOR_CURRENT_BG else COLOR_TRANSPARENT)

            row.setTextViewText(R.id.row_number, "${lesson.lessonNumber}")
            row.setTextColor(R.id.row_number, textColor)

            row.setTextViewText(R.id.row_time, "${lesson.timeStart}–${lesson.timeEnd}")
            row.setTextColor(R.id.row_time, if (isCurrent) COLOR_CURRENT_TEXT else COLOR_SUB)

            row.setTextViewText(
                R.id.row_subject,
                com.example.util.SubjectFormatter.getShortName(lesson.subjectRaw)
            )
            row.setTextColor(R.id.row_subject, textColor)

            val room = lesson.roomFirst.takeIf { it.isNotBlank() } ?: lesson.roomSecond
            row.setTextViewText(R.id.row_room, room.takeIf { it.isNotBlank() }?.let { "каб. $it" } ?: "")
            row.setTextColor(R.id.row_room, COLOR_SUB)

            return row
        }
    }
}
