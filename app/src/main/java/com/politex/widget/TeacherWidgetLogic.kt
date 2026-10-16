package com.politex.widget

import com.politex.data.repository.TeacherDayLogic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TeacherWidgetLesson(
    val lessonNumber: Int,
    val subject: String,
    val groups: List<String>,
    val room: String,
    val start: String,
    val end: String,
    val startMinutes: Int,
    val endMinutes: Int,
)

data class TeacherDayRef(
    val dateString: String,
    val dow: Int,
    val lessons: List<TeacherWidgetLesson>,
)

data class NextLessonRef(
    val dateString: String,
    val dow: Int,
    val lesson: TeacherWidgetLesson,
)

sealed interface TeacherNextState {
    data class Running(val lesson: TeacherWidgetLesson) : TeacherNextState

    data class NextToday(val lesson: TeacherWidgetLesson) : TeacherNextState

    data class EmptyToday(val next: NextLessonRef?) : TeacherNextState

    data class DayDone(val next: NextLessonRef?) : TeacherNextState

    data class DayOff(val next: NextLessonRef?) : TeacherNextState
}

object TeacherWidgetLogic {
    fun shortDow(dow: Int): String = TeacherDayLogic.shortDow(dow)

    fun nextState(
        lessons: List<TeacherWidgetLesson>,
        nowMinutes: Int,
        dow: Int,
        next: NextLessonRef?,
    ): TeacherNextState {
        if (dow == 7 && lessons.isEmpty()) return TeacherNextState.DayOff(next)
        val running = lessons.firstOrNull { it.startMinutes >= 0 && nowMinutes in it.startMinutes..it.endMinutes }
        if (running != null) return TeacherNextState.Running(running)
        val upcoming = lessons.firstOrNull { it.startMinutes >= 0 && it.startMinutes > nowMinutes }
        if (upcoming != null) return TeacherNextState.NextToday(upcoming)
        return if (lessons.isEmpty()) TeacherNextState.EmptyToday(next) else TeacherNextState.DayDone(next)
    }

    fun findNextLesson(days: List<TeacherDayRef>): NextLessonRef? =
        days
            .firstOrNull { it.dow != 7 && it.lessons.isNotEmpty() }
            ?.let { day ->
                val first = day.lessons.minByOrNull { it.lessonNumber } ?: day.lessons.first()
                NextLessonRef(day.dateString, day.dow, first)
            }

    fun cacheKey(
        date: String,
        minute: Int,
        profileName: String,
    ): String = "$date|$minute|$profileName"

    fun updatedLabel(epochMillis: Long): String =
        if (epochMillis <= 0L) {
            ""
        } else {
            "обновлено в " + SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(epochMillis))
        }

    fun todayHeader(
        dayOfMonth: Int,
        month1: Int,
    ): String = String.format(Locale.ROOT, "Сегодня, %02d.%02d", dayOfMonth, month1)

    fun nextLessonText(next: NextLessonRef?): String {
        if (next == null) return ""
        val room = if (next.lesson.room.isNotBlank()) ", каб. ${next.lesson.room}" else ""
        val time = if (next.lesson.start.isNotBlank()) " ${next.lesson.start}" else ""
        return "Следующая — ${shortDow(next.dow)}$time$room"
    }

    fun emptyText(
        title: String,
        next: NextLessonRef?,
    ): String {
        val n = nextLessonText(next)
        return if (n.isBlank()) title else "$title\n$n"
    }

    fun dayOffText(next: NextLessonRef?): String {
        val n = nextLessonText(next)
        return if (n.isBlank()) "Выходной.\nДо 1-й пары в пн" else "Выходной.\n$n"
    }
}
