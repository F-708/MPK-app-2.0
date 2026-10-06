package com.politex.ui.util

import com.politex.data.local.entity.LessonEntity
import com.politex.util.SubgroupPref
import com.politex.util.SubgroupSettings
import com.politex.widget.startMinutes

object RouteStartResolver {

    /**
     * GG2: источник маршрута — либо конкретный кабинет ("" = главный вход),
     * либо вопрос пользователю: две комнаты ПРЕДЫДУЩЕГО split-урока.
     */
    sealed interface RouteStart {
        data class From(val room: String) : RouteStart

        data class Ask(val roomFirst: String, val roomSecond: String) : RouteStart
    }

    fun previousRoomNow(
        lessons: List<LessonEntity>,
        nowMinutes: Int,
    ): String? =
        previousLessonNow(lessons, nowMinutes, Int.MAX_VALUE)
            ?.let { it.roomFirst.ifBlank { it.roomSecond }.trim() }

    /**
     * GG2: предыдущий урок дня (lessonNumber < текущего, макс).
     * Сегодня — дополнительно только уже начавшийся; не сегодня — просто по номеру.
     */
    fun previousLesson(
        lessons: List<LessonEntity>,
        lesson: LessonEntity,
        isToday: Boolean,
        nowMinutes: Int,
    ): LessonEntity? =
        if (isToday) {
            previousLessonNow(lessons, nowMinutes, lesson.lessonNumber)
        } else {
            lessons
                .filter {
                    it.lessonNumber in 1 until lesson.lessonNumber &&
                        (it.roomFirst.isNotBlank() || it.roomSecond.isNotBlank())
                }.maxByOrNull { it.lessonNumber }
        }

    private fun previousLessonNow(
        lessons: List<LessonEntity>,
        nowMinutes: Int,
        maxNumberExclusive: Int,
    ): LessonEntity? =
        lessons
            .filter {
                it.lessonNumber in 1 until maxNumberExclusive &&
                    it.startMinutes() in 0..nowMinutes &&
                    (it.roomFirst.isNotBlank() || it.roomSecond.isNotBlank())
            }.maxByOrNull { it.lessonNumber }

    /**
     * GG2 + GG3: предыдущий НЕ split → его комната (без вопроса); предыдущий split →
     * вопрос с ДВУМЯ его комнатами, но если подгруппа выбрана (pref != NEUTRAL) —
     * комната выбранной подгруппы без вопроса; нет предыдущего → "" (главный вход).
     */
    fun startFromPrevious(
        prev: LessonEntity?,
        pref: SubgroupPref,
    ): RouteStart =
        when {
            prev == null -> RouteStart.From("")
            prev.isSplit && prev.roomFirst.isNotBlank() && prev.roomSecond.isNotBlank() ->
                SubgroupSettings
                    .splitIndex(pref)
                    ?.let { RouteStart.From((if (it == 0) prev.roomFirst else prev.roomSecond).trim()) }
                    ?: RouteStart.Ask(prev.roomFirst.trim(), prev.roomSecond.trim())
            else -> RouteStart.From(prev.roomFirst.ifBlank { prev.roomSecond }.trim())
        }

    fun startForLesson(
        lessons: List<LessonEntity>,
        lesson: LessonEntity,
        isToday: Boolean,
        nowMinutes: Int,
        pref: SubgroupPref,
    ): RouteStart = startFromPrevious(previousLesson(lessons, lesson, isToday, nowMinutes), pref)

    /** Совместимость (GroupScheduleScreen): диалога нет — Ask схлопывается в комнату 1-й подгруппы. */
    fun startForLesson(
        lessons: List<LessonEntity>,
        lesson: LessonEntity,
        isToday: Boolean,
        nowMinutes: Int,
    ): String =
        when (val s = startForLesson(lessons, lesson, isToday, nowMinutes, SubgroupPref.NEUTRAL)) {
            is RouteStart.Ask -> s.roomFirst
            is RouteStart.From -> s.room
        }
}
