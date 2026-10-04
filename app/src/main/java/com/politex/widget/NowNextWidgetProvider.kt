package com.politex.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.politex.R
import com.politex.data.local.entity.LessonEntity
import com.politex.data.model.CollegeBellSchedule
import com.politex.ui.util.RouteStartResolver
import com.politex.util.Dow
import com.politex.util.SubjectFormatter
import kotlinx.coroutines.launch

class NowNextWidgetProvider : AppWidgetProvider() {
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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.clear(context, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NowNextWidgetProvider::class.java))
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, id)) }
        }

        private fun buildViews(
            context: Context,
            appWidgetId: Int,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_now_next)

            val style = WidgetStyle.load(context, appWidgetId)
            val showRoom = WidgetOptions.showRoom(context, appWidgetId)

            views.setInt(R.id.now_next_root, "setBackgroundColor", style.backgroundColor)
            views.setViewVisibility(R.id.iv_idle, android.view.View.GONE)
            views.setTextColor(R.id.tv_now_label, style.subColor)
            views.setTextColor(R.id.tv_next_label, style.subColor)
            views.setTextColor(R.id.tv_now_subject, style.mainColor)
            views.setTextColor(R.id.tv_next_subject, style.mainColor)
            views.setTextColor(R.id.tv_now_details, style.subColor)
            views.setTextColor(R.id.tv_next_details, style.subColor)

            views.setInt(R.id.tv_now_room, "setBackgroundColor", style.highlightColor)
            views.setInt(R.id.tv_next_room, "setBackgroundColor", style.highlightColor)
            views.setTextColor(R.id.tv_now_room, style.mainColor)
            views.setTextColor(R.id.tv_next_room, style.mainColor)

            val now = WidgetData.minuteOfDay(context)
            val lessons = WidgetData.todayLessons(context)

            val current = lessons.firstOrNull { now in it.startMinutes()..it.endMinutes() }

            val nextFromRoom = RouteStartResolver.previousRoomNow(lessons, now)
            val nowFromRoom =
                current?.let { c ->
                    RouteStartResolver.previousRoomNow(lessons.filter { it.lessonNumber < c.lessonNumber }, now)
                }

            val next =
                when {
                    current != null -> lessons.firstOrNull { it.lessonNumber > current.lessonNumber }
                    else -> lessons.firstOrNull { it.startMinutes() > now }
                }

            val groupBlank = WidgetUpdateHelper.getSelectedGroup(context).isBlank()
            val emptyText =
                when {
                    groupBlank -> "Выберите группу в приложении"
                    lessons.isEmpty() -> "Сегодня уроков нет"
                    current == null && next == null -> "Уроки закончились"
                    else -> null
                }
            if (emptyText != null) {
                views.setTextViewText(R.id.tv_empty, emptyText)
                views.setTextColor(R.id.tv_empty, if (groupBlank) style.mainColor else style.subColor)
                views.setViewVisibility(R.id.tv_empty, android.view.View.VISIBLE)
                listOf(R.id.ll_now_block, R.id.ll_next_block, R.id.v_divider).forEach {
                    views.setViewVisibility(it, android.view.View.GONE)
                }
                // CC9: idle-анимация в пустом состоянии (AVD автостартует в ImageView, API24+)
                WidgetUpdateHelper.showIdle(views)
            } else if (current != null) {
                views.setTextViewText(R.id.tv_now_label, "СЕЙЧАС")
                views.setTextViewText(R.id.tv_now_subject, SubjectFormatter.getShortName(current.subjectRaw))
                views.setTextViewText(R.id.tv_now_details, detailsOf(current))
                applyRoomChip(context, views, R.id.tv_now_room, current, showRoom, appWidgetId, "now", nowFromRoom)
                if (next != null) {
                    views.setTextViewText(R.id.tv_next_label, "ДАЛЬШЕ")
                    views.setTextViewText(R.id.tv_next_subject, SubjectFormatter.getShortName(next.subjectRaw))
                    views.setTextViewText(R.id.tv_next_details, detailsOf(next))
                    applyRoomChip(context, views, R.id.tv_next_room, next, showRoom, appWidgetId, "next", nextFromRoom)
                } else {
                    views.setTextViewText(R.id.tv_next_label, "ДАЛЬШЕ")
                    views.setTextViewText(R.id.tv_next_subject, "Уроков больше нет")
                    views.setTextViewText(R.id.tv_next_details, "")
                    views.setViewVisibility(R.id.tv_next_room, android.view.View.GONE)
                }
            } else {

                val bells = CollegeBellSchedule.getBellsForDay(WidgetData.dayOfWeek(context))
                val firstBell = bells.firstOrNull()
                views.setTextViewText(R.id.tv_now_label, "СЕЙЧАС")
                views.setTextViewText(R.id.tv_now_subject, "Перемена")
                views.setTextViewText(
                    R.id.tv_now_details,
                    if (firstBell != null && now < firstBell.startMinutes) {
                        "первый урок в ${firstBell.start}"
                    } else {
                        WidgetData.breakInfo(context, now) ?: "по расписанию звонков"
                    },
                )
                views.setViewVisibility(R.id.tv_now_room, android.view.View.GONE)

                views.setTextViewText(R.id.tv_next_label, "ДАЛЬШЕ")
                views.setTextViewText(R.id.tv_next_subject, SubjectFormatter.getShortName(next!!.subjectRaw))
                views.setTextViewText(R.id.tv_next_details, detailsOf(next))
                applyRoomChip(context, views, R.id.tv_next_room, next, showRoom, appWidgetId, "next", nextFromRoom)
            }

            val open =
                WidgetUpdateHelper.openAppIntent(
                    context,
                    4_000_000 + appWidgetId,
                    "SCHEDULE",
                    scrollTarget = "CURRENT_LESSON",
                )
            views.setOnClickPendingIntent(R.id.now_next_root, open)
            return views
        }

        private fun detailsOf(lesson: LessonEntity): String = "${lesson.timeStart}–${lesson.timeEnd}"

        private fun applyRoomChip(
            context: Context,
            views: RemoteViews,
            viewId: Int,
            lesson: LessonEntity,
            showRoom: Boolean,
            appWidgetId: Int,
            slot: String,

            fromRoom: String? = null,
        ) {
            val rooms =
                listOf(lesson.roomFirst, lesson.roomSecond)
                    .filter { it.isNotBlank() }
                    .distinct()
            if (!showRoom || rooms.isEmpty()) {
                views.setViewVisibility(viewId, android.view.View.GONE)
                return
            }
            val room = rooms.first()
            views.setTextViewText(viewId, "каб. $room")
            views.setViewVisibility(viewId, android.view.View.VISIBLE)

            views.setOnClickPendingIntent(
                viewId,
                WidgetUpdateHelper.openAppIntent(
                    context,
                    1_000_000 + appWidgetId * 10 + if (slot == "now") 1 else 2,
                    "SCHEDULE",
                    room = room,
                    showRoute = true,
                    fromRoom = fromRoom,
                ),
            )
        }
    }
}

object WidgetData {
    // D6: единый источник «минуты суток» — вместо инлайн HOUR*60+MIN по всему коду.
    fun minuteOfDay(cal: java.util.Calendar): Int = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)

    fun minuteOfDay(context: Context): Int = minuteOfDay(java.util.Calendar.getInstance())

    fun dayOfWeek(context: Context): Int = Dow.of(java.util.Calendar.getInstance())

    @Volatile private var cacheMinute = -1

    @Volatile private var cacheGroup = ""

    @Volatile private var cacheLessons: List<LessonEntity> = emptyList()

    fun todayLessons(context: Context): List<LessonEntity> {
        val group = WidgetUpdateHelper.getSelectedGroup(context)
        if (group.isBlank()) return emptyList()
        val minute = minuteOfDay(context)
        if (minute == cacheMinute && group == cacheGroup) return cacheLessons
        val dow = dayOfWeek(context)
        val todayString = todayDateString()
        val result =
            try {
                kotlinx.coroutines.runBlocking {
                    val all =
                        com.politex.data.local.MpkDatabase
                            .getInstance(context)
                            .lessonDao()
                            .getLessonsForDaySync(group, dow)
                    filterTodayLessons(all, todayString)
                }
            } catch (_: Exception) {
                emptyList()
            }

        cacheLessons = result
        cacheGroup = group
        cacheMinute = minute
        return result
    }

    internal fun todayDateString(): String {
        val cal = java.util.Calendar.getInstance()
        return String.format(
            java.util.Locale.ROOT,
            "%02d.%02d.%04d",
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.YEAR),
        )
    }

    internal fun normalizeDate(s: String): String = s.replace('-', '.')

    fun filterTodayLessons(
        all: List<LessonEntity>,
        todayString: String,
    ): List<LessonEntity> {
        val today = all.filter { normalizeDate(it.dateString) == todayString }
        val source = if (today.isNotEmpty()) today else all.filter { it.dateString.isBlank() }
        return source.sortedBy { it.lessonNumber }
    }

    fun breakInfo(
        context: Context,
        minute: Int,
    ): String? {
        val bells = CollegeBellSchedule.getBellsForDay(dayOfWeek(context))
        val bell =
            bells.firstOrNull {
                it.breakAfterMinutes > 0 &&
                    minute > it.endMinutes &&
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
