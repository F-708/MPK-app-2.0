package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.MpkDatabase
import com.example.data.local.entity.LessonEntity
import com.example.data.model.CollegeBellSchedule
import com.example.util.SubjectFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Компактный виджет 2x2 «Текущий урок» (LiveLessonWidgetProvider).
 *
 * ЖЕЛЕЗНЫЕ ПРАВИЛА:
 * 1. Offline-First: читает данные СТРОГО из локальной базы Room без блокировки UI.
 * 2. Разметка оптимизирована под любые DPI лаунчеров (Honor MagicOS, MIUI, OneUI).
 * 3. Нажатие мгновенно открывает приложение на экране расписания.
 */
class LiveLessonWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAppWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            if (ids != null && ids.isNotEmpty()) {
                updateAppWidgets(context, appWidgetManager, ids)
            }
        }
    }

    companion object {
        private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAppWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            providerScope.launch {
                val groupName = WidgetUpdateHelper.getSelectedGroup(context)
                val database = MpkDatabase.getInstance(context)
                val lessonDao = database.lessonDao()

                val calendar = Calendar.getInstance()
                val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> 1
                    Calendar.TUESDAY -> 2
                    Calendar.WEDNESDAY -> 3
                    Calendar.THURSDAY -> 4
                    Calendar.FRIDAY -> 5
                    Calendar.SATURDAY -> 6
                    Calendar.SUNDAY -> 7
                    else -> 1
                }

                val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
                val todayLessons = if (dayOfWeek in 1..6) {
                    lessonDao.getLessonsForDaySync(groupName, dayOfWeek)
                } else {
                    emptyList()
                }

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_live_lesson_2x2)

                    // 1. Устанавливаем бейдж группы
                    views.setTextViewText(R.id.tv_live_group_badge, groupName)

                    // 2. Интент для открытия приложения
                    val openAppIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("EXTRA_OPEN_TAB", "SCHEDULE")
                        putExtra("EXTRA_DAY_OF_WEEK", dayOfWeek)
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        appWidgetId,
                        openAppIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_live_root, pendingIntent)

                    if (todayLessons.isEmpty()) {
                        // Нет занятий на сегодня
                        views.setViewVisibility(R.id.layout_live_content, View.GONE)
                        views.setViewVisibility(R.id.layout_live_empty, View.VISIBLE)
                        views.setTextViewText(
                            R.id.tv_live_countdown,
                            if (dayOfWeek == 7) "Воскресенье" else "Пар нет"
                        )
                        views.setTextViewText(
                            R.id.tv_live_empty_message,
                            if (dayOfWeek == 7) "Завтра понедельник" else "На сегодня пар нет"
                        )
                    } else {
                        // Ищем текущий или следующий урок
                        val activeOrNextLesson = findActiveOrNextLesson(todayLessons, currentMinutes, dayOfWeek)

                        if (activeOrNextLesson != null) {
                            val (lesson, stateInfo) = activeOrNextLesson

                            views.setViewVisibility(R.id.layout_live_content, View.VISIBLE)
                            views.setViewVisibility(R.id.layout_live_empty, View.GONE)

                            // Статус и обратный отсчет
                            views.setTextViewText(R.id.tv_live_countdown, stateInfo.countdownText)

                            // Акроним и название дисциплины
                            val acronym = SubjectFormatter.getAcronym(lesson.subjectRaw)
                            val shortSubject = SubjectFormatter.getShortName(lesson.subjectRaw)
                            views.setTextViewText(R.id.tv_live_acronym, acronym)
                            views.setTextViewText(R.id.tv_live_subject_name, shortSubject)

                            // Время и номер урока
                            val (startTime, endTime) = CollegeBellSchedule.getTimeForLessonNumber(lesson.lessonNumber, dayOfWeek)
                            views.setTextViewText(
                                R.id.tv_live_time_slot,
                                "${lesson.lessonNumber} урок • $startTime – $endTime"
                            )

                            // Аудитория (с учетом подгрупп)
                            val roomDisplay = formatRoomDisplay(lesson)
                            views.setTextViewText(R.id.tv_live_room, roomDisplay)

                            // Преподаватель
                            val teacherDisplay = formatTeacherDisplay(lesson)
                            views.setTextViewText(R.id.tv_live_teacher, teacherDisplay)
                        } else {
                            // Все пары на сегодня уже завершились
                            views.setViewVisibility(R.id.layout_live_content, View.GONE)
                            views.setViewVisibility(R.id.layout_live_empty, View.VISIBLE)
                            views.setTextViewText(R.id.tv_live_countdown, "Завершено")
                            views.setTextViewText(R.id.tv_live_empty_message, "Все пары на сегодня окончены")
                        }
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }

        private data class LessonStateInfo(
            val isCurrent: Boolean,
            val countdownText: String
        )

        private fun findActiveOrNextLesson(
            lessons: List<LessonEntity>,
            currentMinutes: Int,
            dayOfWeek: Int
        ): Pair<LessonEntity, LessonStateInfo>? {
            val sorted = lessons.sortedBy { it.lessonNumber }

            for (lesson in sorted) {
                val (startStr, endStr) = CollegeBellSchedule.getTimeForLessonNumber(lesson.lessonNumber, dayOfWeek)
                val startMins = parseMinutes(startStr)
                val endMins = parseMinutes(endStr)

                // Идет прямо сейчас
                if (currentMinutes in startMins..endMins) {
                    val remaining = endMins - currentMinutes
                    return Pair(
                        lesson,
                        LessonStateInfo(
                            isCurrent = true,
                            countdownText = "До конца: $remaining мин"
                        )
                    )
                }

                // Урок еще не начался
                if (currentMinutes < startMins) {
                    val untilStart = startMins - currentMinutes
                    val text = if (untilStart <= 45) {
                        "Перемена: $untilStart мин"
                    } else {
                        "Начало в $startStr"
                    }
                    return Pair(
                        lesson,
                        LessonStateInfo(
                            isCurrent = false,
                            countdownText = text
                        )
                    )
                }
            }

            return null
        }

        private fun parseMinutes(timeStr: String): Int {
            val parts = timeStr.split(":")
            return if (parts.size >= 2) {
                (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
            } else 0
        }

        private fun formatRoomDisplay(lesson: LessonEntity): String {
            return if (lesson.isSplit) {
                val r1 = lesson.roomFirst.ifBlank { "—" }
                val r2 = lesson.roomSecond.ifBlank { "—" }
                "$r1 / $r2"
            } else {
                val r = lesson.roomFirst.ifBlank { "—" }
                "каб. $r"
            }
        }

        private fun formatTeacherDisplay(lesson: LessonEntity): String {
            return if (lesson.isSplit && lesson.teacherSecond.isNotBlank()) {
                "${lesson.teacherFirst} / ${lesson.teacherSecond}"
            } else {
                lesson.teacherFirst
            }
        }
    }
}
