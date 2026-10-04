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
        // QA#2: buildViews читает Room (WidgetData.todayLessons) — нельзя на main из
        // onReceive. goAsync держит процесс живым, пересборка уходит на IO.
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

            // V21: старт маршрута — «где студент сейчас»: для «ДАЛЬШЕ» это кабинет текущего
            // слота, для «СЕЙЧАС» — кабинет предыдущего (в том же слоте from == to и маршрут
            // вырождается). null → карта построит маршрут от главного входа (N8).
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

            // V3: пустые состояния — одна строка по центру виджета вместо двух
            // полупустых блоков. RemoteViews каждый раз свежий, видимость блоков
            // возвращается к дефолтной (visible) сама.
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
                // V3: до первого звонка — «Перемена» с внятной причиной (W4), время из bells
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
                // current == null && next == null исключён выше → next здесь всегда есть
                views.setTextViewText(R.id.tv_next_label, "ДАЛЬШЕ")
                views.setTextViewText(R.id.tv_next_subject, SubjectFormatter.getShortName(next!!.subjectRaw))
                views.setTextViewText(R.id.tv_next_details, detailsOf(next))
                applyRoomChip(context, views, R.id.tv_next_room, next, showRoom, appWidgetId, "next", nextFromRoom)
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

        private fun detailsOf(lesson: LessonEntity): String = "${lesson.timeStart}–${lesson.timeEnd}"

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
            // V21: кабинет «где студент сейчас» — старт маршрута вместо главного входа
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

            val intent =
                Intent(context, com.politex.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("EXTRA_OPEN_TAB", "SCHEDULE")
                    .putExtra("EXTRA_ROOM", room)
                    // N8: тап по кабинету в NowNext — карта С маршрутом
                    .putExtra("EXTRA_SHOW_ROUTE", true)
            // V21: старт маршрута — кабинет текущего слота, если он есть (иначе главный вход)
            if (!fromRoom.isNullOrBlank()) intent.putExtra("EXTRA_FROM_ROOM", fromRoom)
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

    fun dayOfWeek(context: Context): Int = Dow.of(java.util.Calendar.getInstance())

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
        // Ключ (cacheMinute) пишем последним: читатель, увидевший новую минуту, видит и свежие данные
        cacheLessons = result
        cacheGroup = group
        cacheMinute = minute
        return result
    }

    /**
     * QA#10: единое «сегодня» для всех виджетов — dd.MM.yyyy, Locale.ROOT. Локаль устройства
     * может давать не-ASCII цифры («%02d».format без локали), и строковое сравнение дат ломается.
     */
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

    /** QA#10: dateString из БД встречается в двух видах (dd.MM.yyyy / dd-MM-yyyy) — к одному перед сравнением. */
    internal fun normalizeDate(s: String): String = s.replace('-', '.')

    /**
     * WID-01: только СЕГОДНЯШНИЕ строки (dateString == todayString после нормализации,
     * формат dd.MM.yyyy — фиксированная ширина, сравнение через == корректно). Если сегодняшней
     * синхронизации не было — фолбэк на недельный шаблон (dateString.isBlank()). Чужие даты
     * не показываем никогда: нет ни того, ни другого — пустой список.
     */
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
