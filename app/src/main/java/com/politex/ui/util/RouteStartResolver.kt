package com.politex.ui.util

import com.politex.data.local.entity.LessonEntity
import com.politex.widget.startMinutes

object RouteStartResolver {

    fun previousRoomNow(
        lessons: List<LessonEntity>,
        nowMinutes: Int,
    ): String? = lessons
        .filter {
            it.lessonNumber > 0 &&
                it.startMinutes() in 0..nowMinutes &&
                (it.roomFirst.isNotBlank() || it.roomSecond.isNotBlank())
        }.maxByOrNull { it.lessonNumber }

        ?.let { it.roomFirst.ifBlank { it.roomSecond }.trim() }

    fun startForLesson(
        lessons: List<LessonEntity>,
        lesson: LessonEntity,
        isToday: Boolean,
        nowMinutes: Int,
    ): String =
        if (isToday) {
            previousRoomNow(lessons, nowMinutes) ?: ""
        } else {
            lessons
                .filter { it.lessonNumber < lesson.lessonNumber && it.roomFirst.isNotBlank() }
                .maxByOrNull { it.lessonNumber }
                ?.roomFirst
                ?.trim() ?: ""
        }
}
