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
import com.politex.data.model.CollegeBellSchedule
import com.politex.util.SubjectFormatter

class NowNextWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
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

        private fun buildViews(
            context: Context,
            appWidgetId: Int,
        ): RemoteViews {
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
            // N7: чип кабинета — фон highlightColor, текст mainColor. Пара даёт
            // контраст во всех 4 стилях: LIGHT (тёмный текст по светло-голубому),
            // DARK/BLUE/SKY (белый текст по тёмно-синему). Было: текст mainColor по
            // почти-белой плашке bg_widget_room_chip — белый по белому в DARK/BLUE/SKY.
            views.setInt(R.id.tv_now_room, "setBackgroundColor", style.highlightColor)
            views.setInt(R.id.tv_next_room, "setBackgroundColor", style.highlightColor)
            views.setTextColor(R.id.tv_now_room, style.mainColor)
            views.setTextColor(R.id.tv_next_room, style.mainColor)

            val now = WidgetData.minuteOfDay(context)
            val lessons = WidgetData.todayLessons(context)

            val current = lessons.firstOrNull { now in it.startMinutes()..it.endMinutes() }

            val next =
                when {
                    current != null -> lessons.firstOrNull { it.lessonNumber > current.lessonNumber }
                    else -> lessons.firstOrNull { it.startMinutes() > now }
                }

            if (current != null) {
                views.setTextViewText(R.id.tv_now_label, "СЕЙЧАС")
                views.setTextViewText(R.id.tv_now_subject, SubjectFormatter.getShortName(current.subjectRaw))
                views.setTextViewText(R.id.tv_now_details, detailsOf(current, showRoom))
                applyRoomChip(context, views, R.id.tv_now_room, current, showRoom, appWidgetId, "now")
            } else {
                // W8: пусто из-за группы не выбрана ≠ «уроков нет»
                val groupBlank = WidgetUpdateHelper.getSelectedGroup(context).isBlank()
                views.setTextViewText(
                    R.id.tv_now_label,
                    if (lessons.isEmpty()) "СЕГОДНЯ" else "СЕЙЧАС",
                )
                views.setTextViewText(
                    R.id.tv_now_subject,
                    when {
                        groupBlank -> "Выберите группу в приложении"
                        lessons.isEmpty() -> "Уроков нет"
                        else -> "Перемена"
                    },
                )
                // W4: причина пустой строки «Перемена» — не пустое место
                views.setTextViewText(
                    R.id.tv_now_details,
                    when {
                        groupBlank || lessons.isEmpty() -> ""
                        else -> WidgetData.breakInfo(context, now) ?: "по расписанию звонков"
                    },
                )
                views.setViewVisibility(R.id.tv_now_room, android.view.View.GONE)
            }

            if (next != null) {
                views.setTextViewText(R.id.tv_next_label, "ДАЛЬШЕ")
                views.setTextViewText(R.id.tv_next_subject, SubjectFormatter.getShortName(next.subjectRaw))
                views.setTextViewText(R.id.tv_next_details, detailsOf(next, showRoom))
                applyRoomChip(context, views, R.id.tv_next_room, next, showRoom, appWidgetId, "next")
            } else {
                views.setTextViewText(R.id.tv_next_label, "ДАЛЬШЕ")
                views.setTextViewText(
                    R.id.tv_next_subject,
                    if (lessons.isEmpty()) "—" else "Уроков больше нет",
                )
                views.setTextViewText(R.id.tv_next_details, "")
                views.setViewVisibility(R.id.tv_next_room, android.view.View.GONE)
            }

            val open =
                PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    Intent(context, com.politex.MainActivity::class.java)
                        // CLEAR_TOP|SINGLE_TOP: тёплый тап по виджету доходит до onNewIntent,
                        // а не плодит копии активити
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra("EXTRA_OPEN_TAB", "SCHEDULE")
                        .putExtra("EXTRA_SCROLL_TARGET", "CURRENT_LESSON"),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            views.setOnClickPendingIntent(R.id.now_next_root, open)
            return views
        }

        private fun detailsOf(
            lesson: LessonEntity,
            showRoom: Boolean,
        ): String = "${lesson.timeStart}–${lesson.timeEnd}"

        /**
         * Б7: кабинет в виджете — отдельная кликабельная кнопка, а не хвост строки со
         * временем. Тап открывает приложение сразу на карте с этим кабинетом.
         * Пустой/скрытый кабинет прячем (showRoom выключен или у пары его нет).
         *
         * PendingIntent — по (appWidgetId + слот), иначе «сейчас» и «дальше» делят
         * один requestCode и второй тап открывает кабинет первого.
         */
        private fun applyRoomChip(
            context: Context,
            views: RemoteViews,
            viewId: Int,
            lesson: LessonEntity,
            showRoom: Boolean,
            appWidgetId: Int,
            slot: String,
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

            val intent =
                Intent(context, com.politex.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("EXTRA_OPEN_TAB", "SCHEDULE")
                    .putExtra("EXTRA_ROOM", room)
                    // N8: тап по кабинету в NowNext — карта С маршрутом от главного входа
                    .putExtra("EXTRA_SHOW_ROUTE", true)
            views.setOnClickPendingIntent(
                viewId,
                PendingIntent.getActivity(
                    context,
                    appWidgetId * 10 + if (slot == "now") 1 else 2,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
    }
}

object WidgetData {
    fun minuteOfDay(context: Context): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    }

    fun dayOfWeek(context: Context): Int = when (
        java.util.Calendar
            .getInstance()
            .get(java.util.Calendar.DAY_OF_WEEK)
    ) {
        java.util.Calendar.MONDAY -> 1
        java.util.Calendar.TUESDAY -> 2
        java.util.Calendar.WEDNESDAY -> 3
        java.util.Calendar.THURSDAY -> 4
        java.util.Calendar.FRIDAY -> 5
        java.util.Calendar.SATURDAY -> 6
        else -> 7
    }

    // O1: результат не зависит от appWidgetId и меняется раз в минуту, а runBlocking в Room
    // на каждом пересборе виджета = «виджет пропал». Кэш по (минута + группа).
    // Вызывается из разных потоков (будильник, BellTimerService, WorkManager) — поля @Volatile.
    @Volatile private var cacheMinute = -1

    @Volatile private var cacheGroup = ""

    @Volatile private var cacheLessons: List<LessonEntity> = emptyList()

    fun todayLessons(context: Context): List<LessonEntity> {
        val group = WidgetUpdateHelper.getSelectedGroup(context)
        if (group.isBlank()) return emptyList()
        val minute = minuteOfDay(context)
        if (minute == cacheMinute && group == cacheGroup) return cacheLessons
        val dow = dayOfWeek(context)
        val cal = java.util.Calendar.getInstance()
        val todayString =
            "%02d.%02d.%04d".format(
                cal.get(java.util.Calendar.DAY_OF_MONTH),
                cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.YEAR),
            )
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
        // Ключ (cacheMinute) пишем последним: читатель, увидевший новую минуту, видит и свежие данные
        cacheLessons = result
        cacheGroup = group
        cacheMinute = minute
        return result
    }

    /**
     * WID-01: только СЕГОДНЯШНИЕ строки (dateString == todayString, формат dd.MM.yyyy —
     * фиксированная ширина, сравнение через == корректно). Если сегодняшней синхронизации
     * не было — фолбэк на недельный шаблон (dateString.isBlank()). Чужие даты не показываем
     * никогда: нет ни того, ни другого — пустой список.
     */
    fun filterTodayLessons(
        all: List<LessonEntity>,
        todayString: String,
    ): List<LessonEntity> {
        val today = all.filter { it.dateString == todayString }
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
