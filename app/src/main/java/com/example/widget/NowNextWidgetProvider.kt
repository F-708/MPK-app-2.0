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
import com.example.data.model.CollegeBellSchedule
import com.example.util.SubjectFormatter

class NowNextWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, id)) }
        WidgetAlarm.scheduleNext(context)
    }

    override fun onEnabled(context: Context) = WidgetAlarm.scheduleNext(context)

    companion object {

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NowNextWidgetProvider::class.java))
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, id)) }
        }

        private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_now_next)

            val style = WidgetStyle.load(context, appWidgetId)
            val showRoom = WidgetOptions.showRoom(context, appWidgetId)

            views.setInt(R.id.now_next_root, "setBackgroundColor", style.backgroundColor)
            views.setTextColor(R.id.tv_now_label, style.subColor)
            views.setTextColor(R.id.tv_next_label, style.subColor)
            views.setTextColor(R.id.tv_now_subject, style.mainColor)
            views.setTextColor(R.id.tv_next_subject, style.mainColor)
            views.setTextColor(R.id.tv_now_details, style.subColor)
            views.setTextColor(R.id.tv_next_details, style.subColor)

            val now = WidgetData.minuteOfDay(context)
            val lessons = WidgetData.todayLessons(context)

            val current = lessons.firstOrNull { now in it.startMinutes()..it.endMinutes() }

            val next = when {
                current != null -> lessons.firstOrNull { it.lessonNumber > current.lessonNumber }
                else -> lessons.firstOrNull { it.startMinutes() > now }
            }

            if (current != null) {
                views.setTextViewText(R.id.tv_now_label, "СЕЙЧАС")
                views.setTextViewText(R.id.tv_now_subject, SubjectFormatter.getShortName(current.subjectRaw))
                views.setTextViewText(R.id.tv_now_details, detailsOf(current, showRoom))
            } else {

                views.setTextViewText(
                    R.id.tv_now_label,
                    if (lessons.isEmpty()) "СЕГОДНЯ" else "СЕЙЧАС"
                )
                views.setTextViewText(
                    R.id.tv_now_subject,
                    if (lessons.isEmpty()) "Уроков нет" else "Перемена"
                )
                val breakInfo = WidgetData.breakInfo(context, now)
                views.setTextViewText(R.id.tv_now_details, breakInfo ?: "")
            }

            if (next != null) {
                views.setTextViewText(R.id.tv_next_label, "ДАЛЬШЕ")
                views.setTextViewText(R.id.tv_next_subject, SubjectFormatter.getShortName(next.subjectRaw))
                views.setTextViewText(R.id.tv_next_details, detailsOf(next, showRoom))
            } else {
                views.setTextViewText(R.id.tv_next_label, "ДАЛЬШЕ")
                views.setTextViewText(
                    R.id.tv_next_subject,
                    if (lessons.isEmpty()) "—" else "Уроков больше нет"
                )
                views.setTextViewText(R.id.tv_next_details, "")
            }

            val open = PendingIntent.getActivity(
                context,
                appWidgetId,
                Intent(context, com.example.MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.now_next_root, open)
            return views
        }

        private fun detailsOf(lesson: LessonEntity, showRoom: Boolean): String {
            val time = "${lesson.timeStart}–${lesson.timeEnd}"
            if (!showRoom) return time
            val rooms = listOf(lesson.roomFirst, lesson.roomSecond)
                .filter { it.isNotBlank() }
                .distinct()
            return if (rooms.isEmpty()) time else "$time • каб. ${rooms.joinToString("/")}"
        }
    }
}

object WidgetData {

    fun minuteOfDay(context: Context): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    }

    fun dayOfWeek(context: Context): Int =
        when (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> 1
            java.util.Calendar.TUESDAY -> 2
            java.util.Calendar.WEDNESDAY -> 3
            java.util.Calendar.THURSDAY -> 4
            java.util.Calendar.FRIDAY -> 5
            java.util.Calendar.SATURDAY -> 6
            else -> 7
        }

    fun todayLessons(context: Context): List<LessonEntity> {
        val group = WidgetUpdateHelper.getSelectedGroup(context)
        if (group.isBlank()) return emptyList()
        val dow = dayOfWeek(context)
        return try {
            kotlinx.coroutines.runBlocking {
                val all = com.example.data.local.MpkDatabase.getInstance(context)
                    .lessonDao().getLessonsForDaySync(group, dow)
                val latestDate = all.filter { it.dateString.isNotBlank() }
                    .maxOfOrNull { it.dateString }
                val today = if (latestDate != null) {
                    all.filter { it.dateString == latestDate || it.dateString.isBlank() }
                } else all
                today.sortedBy { it.lessonNumber }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun breakInfo(context: Context, minute: Int): String? {
        val bells = CollegeBellSchedule.getBellsForDay(dayOfWeek(context))
        val bell = bells.firstOrNull {
            it.breakAfterMinutes > 0 && minute > it.endMinutes &&
                minute < it.endMinutes + it.breakAfterMinutes
        } ?: return null
        val next = bells.getOrNull(bells.indexOf(bell) + 1) ?: return null
        return "Перемена до ${next.start}"
    }
}

internal fun LessonEntity.startMinutes(): Int = parseHhMm(timeStart)
internal fun LessonEntity.endMinutes(): Int = parseHhMm(timeEnd)

private fun parseHhMm(value: String): Int {
    val parts = value.split(":")
    if (parts.size < 2) return -1
    val h = parts[0].trim().toIntOrNull() ?: return -1
    val m = parts[1].trim().toIntOrNull() ?: return -1
    return h * 60 + m
}
