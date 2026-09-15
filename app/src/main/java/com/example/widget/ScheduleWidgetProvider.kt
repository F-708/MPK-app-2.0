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
import com.example.util.GroupParser
import com.example.util.SubjectFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Информативный виджет 4x2 «Расписание на день» (ScheduleWidgetProvider).
 *
 * ЖЕЛЕЗНЫЕ ПРАВИЛА:
 * 1. Offline-First: читает данные СТРОГО из локальной базы Room.
 * 2. Поддержка переключения дня («Сегодня» <-> «Завтра» / «Понедельник») прямо на виджете.
 * 3. Подгруппы 1 и 2 отображаются аккуратными микро-бейджами без обрезания текста.
 */
class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAppWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_DAY) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val prefs = WidgetUpdateHelper.getPrefs(context)
                val currentOffset = prefs.getInt(PREF_DAY_OFFSET_PREFIX + appWidgetId, 0)
                val newOffset = if (currentOffset == 0) 1 else 0
                prefs.edit().putInt(PREF_DAY_OFFSET_PREFIX + appWidgetId, newOffset).apply()

                val appWidgetManager = AppWidgetManager.getInstance(context)
                updateAppWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_DAY = "com.example.widget.ACTION_TOGGLE_DAY"
        private const val PREF_DAY_OFFSET_PREFIX = "widget_day_offset_"
        private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAppWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            providerScope.launch {
                val groupName = WidgetUpdateHelper.getSelectedGroup(context)
                val groupInfo = GroupParser.parse(groupName)
                val isFirstCourse = groupInfo?.course == 1

                val database = MpkDatabase.getInstance(context)
                val lessonDao = database.lessonDao()

                val calendar = Calendar.getInstance()
                val currentDayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> 1
                    Calendar.TUESDAY -> 2
                    Calendar.WEDNESDAY -> 3
                    Calendar.THURSDAY -> 4
                    Calendar.FRIDAY -> 5
                    Calendar.SATURDAY -> 6
                    Calendar.SUNDAY -> 7
                    else -> 1
                }

                for (appWidgetId in appWidgetIds) {
                    val prefs = WidgetUpdateHelper.getPrefs(context)
                    val offset = prefs.getInt(PREF_DAY_OFFSET_PREFIX + appWidgetId, 0)

                    // Рассчитываем целевой день недели и подписи
                    val (targetDay, dayTitle, toggleBtnText) = computeTargetDayInfo(
                        currentDayOfWeek = currentDayOfWeek,
                        offset = offset,
                        isFirstCourse = isFirstCourse
                    )

                    val lessons = lessonDao.getLessonsForDaySync(groupName, targetDay)
                        .sortedBy { it.lessonNumber }

                    val views = RemoteViews(context.packageName, R.layout.widget_schedule_4x2)

                    // 1. Бейдж группы и заголовок дня
                    views.setTextViewText(R.id.tv_schedule_group_badge, groupName)
                    views.setTextViewText(R.id.tv_schedule_day_title, dayTitle)
                    views.setTextViewText(R.id.tv_schedule_toggle_text, toggleBtnText)

                    // 2. Интент для переключения дня
                    val toggleIntent = Intent(context, ScheduleWidgetProvider::class.java).apply {
                        action = ACTION_TOGGLE_DAY
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                    val togglePendingIntent = PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        toggleIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.btn_schedule_toggle_day, togglePendingIntent)

                    // 3. Интент для открытия приложения
                    val openAppIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("EXTRA_OPEN_TAB", "SCHEDULE")
                        putExtra("EXTRA_DAY_OF_WEEK", targetDay)
                    }
                    val openAppPendingIntent = PendingIntent.getActivity(
                        context,
                        appWidgetId + 1000,
                        openAppIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_schedule_root, openAppPendingIntent)

                    // 4. Заполнение строк пар
                    if (lessons.isEmpty()) {
                        views.setViewVisibility(R.id.layout_schedule_list, View.GONE)
                        views.setViewVisibility(R.id.layout_schedule_empty, View.VISIBLE)
                        views.setTextViewText(
                            R.id.tv_schedule_empty_msg,
                            if (targetDay == 7) "Воскресенье — выходной день" else "На этот день пар нет\nили расписание ещё не загружено"
                        )
                    } else {
                        views.setViewVisibility(R.id.layout_schedule_list, View.VISIBLE)
                        views.setViewVisibility(R.id.layout_schedule_empty, View.GONE)

                        val rowIds = listOf(
                            R.id.row_lesson_1,
                            R.id.row_lesson_2,
                            R.id.row_lesson_3,
                            R.id.row_lesson_4,
                            R.id.row_lesson_5,
                            R.id.row_lesson_6
                        )

                        val numIds = listOf(R.id.tv_row1_num, R.id.tv_row2_num, R.id.tv_row3_num, R.id.tv_row4_num, R.id.tv_row5_num, R.id.tv_row6_num)
                        val timeIds = listOf(R.id.tv_row1_time, R.id.tv_row2_time, R.id.tv_row3_time, R.id.tv_row4_time, R.id.tv_row5_time, R.id.tv_row6_time)
                        val subjIds = listOf(R.id.tv_row1_subject, R.id.tv_row2_subject, R.id.tv_row3_subject, R.id.tv_row4_subject, R.id.tv_row5_subject, R.id.tv_row6_subject)
                        val sg1Ids = listOf(R.id.tv_row1_subgroup1, R.id.tv_row2_subgroup1, R.id.tv_row3_subgroup1, R.id.tv_row4_subgroup1, R.id.tv_row5_subgroup1, R.id.tv_row6_subgroup1)
                        val sg2Ids = listOf(R.id.tv_row1_subgroup2, R.id.tv_row2_subgroup2, R.id.tv_row3_subgroup2, R.id.tv_row4_subgroup2, R.id.tv_row5_subgroup2, R.id.tv_row6_subgroup2)
                        val roomIds = listOf(R.id.tv_row1_room, R.id.tv_row2_room, R.id.tv_row3_room, R.id.tv_row4_room, R.id.tv_row5_room, R.id.tv_row6_room)

                        for (i in 0 until 6) {
                            if (i < lessons.size) {
                                val lesson = lessons[i]
                                views.setViewVisibility(rowIds[i], View.VISIBLE)

                                views.setTextViewText(numIds[i], "${lesson.lessonNumber}")
                                val (start, _) = CollegeBellSchedule.getTimeForNumber(lesson.lessonNumber, targetDay)
                                views.setTextViewText(timeIds[i], start)

                                val shortSubj = SubjectFormatter.getShortName(lesson.subjectRaw)
                                views.setTextViewText(subjIds[i], shortSubj)

                                if (lesson.isSplit) {
                                    views.setViewVisibility(sg1Ids[i], View.VISIBLE)
                                    views.setViewVisibility(sg2Ids[i], View.VISIBLE)
                                    val r1 = lesson.roomFirst.ifBlank { "—" }
                                    val r2 = lesson.roomSecond.ifBlank { "—" }
                                    views.setTextViewText(roomIds[i], "$r1 / $r2")
                                } else {
                                    views.setViewVisibility(sg1Ids[i], View.GONE)
                                    views.setViewVisibility(sg2Ids[i], View.GONE)
                                    val r = lesson.roomFirst.ifBlank { "—" }
                                    views.setTextViewText(roomIds[i], r)
                                }
                            } else {
                                views.setViewVisibility(rowIds[i], View.GONE)
                            }
                        }
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }

        private data class DayInfo(
            val targetDay: Int,
            val title: String,
            val toggleButtonText: String
        )

        private fun computeTargetDayInfo(
            currentDayOfWeek: Int,
            offset: Int,
            isFirstCourse: Boolean
        ): DayInfo {
            val dayNames = arrayOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")

            if (offset == 0) {
                // Режим "Сегодня"
                return when (currentDayOfWeek) {
                    7 -> DayInfo(
                        targetDay = 1,
                        title = "Понедельник (Завтра)",
                        toggleButtonText = "Сегодня"
                    )
                    6 -> {
                        if (isFirstCourse) {
                            DayInfo(targetDay = 6, title = "Сегодня • Суббота", toggleButtonText = "Пн")
                        } else {
                            DayInfo(targetDay = 1, title = "Понедельник", toggleButtonText = "Пн")
                        }
                    }
                    else -> {
                        val name = dayNames[currentDayOfWeek - 1]
                        val nextBtnText = when {
                            currentDayOfWeek == 5 && !isFirstCourse -> "Пн"
                            currentDayOfWeek == 5 && isFirstCourse -> "Завтра"
                            else -> "Завтра"
                        }
                        DayInfo(targetDay = currentDayOfWeek, title = "Сегодня • $name", toggleButtonText = nextBtnText)
                    }
                }
            } else {
                // Режим "Следующий учебный день"
                return when (currentDayOfWeek) {
                    5 -> {
                        if (isFirstCourse) {
                            DayInfo(targetDay = 6, title = "Завтра • Суббота", toggleButtonText = "Сегодня")
                        } else {
                            DayInfo(targetDay = 1, title = "Понедельник", toggleButtonText = "Сегодня")
                        }
                    }
                    6, 7 -> DayInfo(targetDay = 1, title = "Понедельник", toggleButtonText = "Сегодня")
                    else -> {
                        val nextDay = currentDayOfWeek + 1
                        val name = dayNames[nextDay - 1]
                        DayInfo(targetDay = nextDay, title = "Завтра • $name", toggleButtonText = "Сегодня")
                    }
                }
            }
        }
    }
}
