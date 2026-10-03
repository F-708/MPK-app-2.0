package com.politex.ui.util

import com.politex.data.local.entity.LessonEntity
import com.politex.widget.startMinutes

/**
 * R2: точка старта маршрута на карте — кабинет, где пользователь физически находится:
 * урок с наибольшим lessonNumber среди уже НАЧАВШИХСЯ сегодня (идущий сейчас — тоже
 * «начавшийся»). До первого урока — null (маршрут от главного входа).
 *
 * Чистая функция (время строкой из LessonEntity.timeStart), чтобы юнит-тест без Robolectric.
 */
object RouteStartResolver {
    /** Номер кабинета текущей/предыдущей пары или null, если ничего не началось / кабинета нет. */
    fun previousRoomNow(
        lessons: List<LessonEntity>,
        nowMinutes: Int,
    ): String? = lessons
        .filter {
            it.lessonNumber > 0 &&
                it.startMinutes() in 0..nowMinutes &&
                (it.roomFirst.isNotBlank() || it.roomSecond.isNotBlank())
        }.maxByOrNull { it.lessonNumber }
        // ponytail: подгруппу split-пары не определяем — roomFirst, как в findGroupRoomNow
        ?.let { it.roomFirst.ifBlank { it.roomSecond }.trim() }

    /**
     * V21: старт маршрута для тапа по КОНКРЕТНОМУ уроку — маршрут теперь всегда.
     * Сегодня: кабинет, где пользователь физически находится (previousRoomNow), иначе ""
     * (от главного входа). Другой день (завтра/архив): кабинет ближайшего предыдущего
     * урока — логичная точка «откуда пойдём», иначе "".
     *
     * ponytail: для не-сегодня только roomFirst (split-подгруппу на будущем дне не угадать).
     */
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
