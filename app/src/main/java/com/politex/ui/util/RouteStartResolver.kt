package com.politex.ui.util

import com.politex.data.local.entity.LessonEntity
import com.politex.util.SubgroupPref
import com.politex.util.SubgroupSettings

object RouteStartResolver {

    /**
     * GG2: источник маршрута — либо конкретный кабинет ("" = главный вход),
     * либо вопрос пользователю: две комнаты ПРЕДЫДУЩЕГО split-урока.
     */
    sealed interface RouteStart {
        data class From(val room: String) : RouteStart

        data class Ask(val roomFirst: String, val roomSecond: String) : RouteStart
    }

    /**
     * II1: предыдущий урок ОТНОСИТЕЛЬНО ЦЕЛЕВОГО — максимальный номер меньше
     * lesson.lessonNumber с непустой комнатой. Текущее время не влияет:
     * тап по 3-му уроку утром ведёт маршрут от кабинета 2-го.
     */
    fun previousLesson(
        lessons: List<LessonEntity>,
        lesson: LessonEntity,
    ): LessonEntity? =
        lessons
            .filter {
                it.lessonNumber in 1 until lesson.lessonNumber &&
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
        pref: SubgroupPref,
    ): RouteStart = startFromPrevious(previousLesson(lessons, lesson), pref)

    /** Совместимость (GroupScheduleScreen): диалога нет — Ask схлопывается в комнату 1-й подгруппы. */
    fun startForLesson(
        lessons: List<LessonEntity>,
        lesson: LessonEntity,
    ): String = startRoomForLesson(lessons, lesson, SubgroupPref.NEUTRAL)

    /** HH1: то же с pref подгруппы; для виджетов (диалога нет) Ask схлопывается в roomFirst. */
    fun startRoomForLesson(
        lessons: List<LessonEntity>,
        lesson: LessonEntity,
        pref: SubgroupPref,
    ): String =
        when (val s = startForLesson(lessons, lesson, pref)) {
            is RouteStart.Ask -> s.roomFirst
            is RouteStart.From -> s.room
        }
}
