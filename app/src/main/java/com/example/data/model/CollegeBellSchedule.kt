package com.example.data.model

import java.util.Calendar

/**
 * Тип расписания звонков в зависимости от дня недели.
 */
enum class BellScheduleType(val title: String, val subtitle: String) {
    STANDARD("Основное (Пн-Ср, Пт)", "12 уроков / 6 пар"),
    THURSDAY("Четверг (Инфочас)", "С информационным часом"),
    SATURDAY("Суббота", "8 уроков / 4 пары")
}

/**
 * Элемент расписания звонков (урок или специальное событие вроде Информационного часа).
 */
data class BellItem(
    val lessonNumber: Int,          // 1..12 (или 0 для инфочаса)
    val pairNumber: Int? = null,    // 1..6 если применимо
    val title: String,
    val start: String,
    val end: String,
    val breakAfterMinutes: Int = 10,
    val isBigBreak: Boolean = false,
    val isInfoHour: Boolean = false
) {
    val displayRange: String get() = "$start – $end"

    val startMinutes: Int get() {
        val parts = start.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    val endMinutes: Int get() {
        val parts = end.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }
}

/**
 * Расписание звонков ГУО «Минский государственный политехнический колледж» (МГПК).
 * Утверждено директором колледжа на 2026 учебный год.
 */
object CollegeBellSchedule {

    const val DISCLAIMER: String =
        "Справочная информация: утвержденное расписание звонков колледжа МГПК на 2026 г. Сокращённые звонки не синхронизируются автоматически."

    // А) Понедельник, вторник, среда, пятница (Стандартный график)
    val STANDARD_BELLS: List<BellItem> = listOf(
        BellItem(1, 1, "1 урок (1 пара)", "08:15", "09:00", breakAfterMinutes = 10),
        BellItem(2, 1, "2 урок (1 пара)", "09:10", "09:55", breakAfterMinutes = 25, isBigBreak = true),
        BellItem(3, 2, "3 урок (2 пара)", "10:20", "11:05", breakAfterMinutes = 10),
        BellItem(4, 2, "4 урок (2 пара)", "11:15", "12:00", breakAfterMinutes = 25, isBigBreak = true),
        BellItem(5, 3, "5 урок (3 пара)", "12:25", "13:10", breakAfterMinutes = 10),
        BellItem(6, 3, "6 урок (3 пара)", "13:20", "14:05", breakAfterMinutes = 10),
        BellItem(7, 4, "7 урок (4 пара)", "14:15", "15:00", breakAfterMinutes = 10),
        BellItem(8, 4, "8 урок (4 пара)", "15:10", "15:55", breakAfterMinutes = 10),
        BellItem(9, 5, "9 урок (5 пара)", "16:05", "16:50", breakAfterMinutes = 10),
        BellItem(10, 5, "10 урок (5 пара)", "17:00", "17:45", breakAfterMinutes = 10),
        BellItem(11, 6, "11 урок (6 пара)", "17:55", "18:40", breakAfterMinutes = 10),
        BellItem(12, 6, "12 урок (6 пара)", "18:50", "19:35", breakAfterMinutes = 0)
    )

    // Б) Четверг (Особый график с Информационным часом)
    val THURSDAY_BELLS: List<BellItem> = listOf(
        BellItem(1, 1, "1 урок (1 пара)", "08:15", "09:00", breakAfterMinutes = 10),
        BellItem(2, 1, "2 урок (1 пара)", "09:10", "09:55", breakAfterMinutes = 25, isBigBreak = true),
        BellItem(3, 2, "3 урок (2 пара)", "10:20", "11:05", breakAfterMinutes = 10),
        BellItem(4, 2, "4 урок (2 пара)", "11:15", "12:00", breakAfterMinutes = 25, isBigBreak = true),
        BellItem(5, 3, "5 урок (3 пара)", "12:25", "13:10", breakAfterMinutes = 10),
        BellItem(6, 3, "6 урок (3 пара)", "13:20", "14:05", breakAfterMinutes = 10),
        BellItem(0, null, "Информационный час", "14:15", "14:35", breakAfterMinutes = 10, isInfoHour = true),
        BellItem(7, 4, "7 урок (4 пара)", "14:45", "15:30", breakAfterMinutes = 10),
        BellItem(8, 4, "8 урок (4 пара)", "15:40", "16:25", breakAfterMinutes = 10),
        BellItem(9, 5, "9 урок (5 пара)", "16:35", "17:20", breakAfterMinutes = 10),
        BellItem(10, 5, "10 урок (5 пара)", "17:30", "18:15", breakAfterMinutes = 10),
        BellItem(11, 6, "11 урок (6 пара)", "18:25", "19:10", breakAfterMinutes = 10),
        BellItem(12, 6, "12 урок (6 пара)", "19:20", "20:05", breakAfterMinutes = 0)
    )

    // В) Суббота
    val SATURDAY_BELLS: List<BellItem> = listOf(
        BellItem(1, 1, "1 урок (1 пара)", "08:15", "09:00", breakAfterMinutes = 10),
        BellItem(2, 1, "2 урок (1 пара)", "09:10", "09:55", breakAfterMinutes = 10),
        BellItem(3, 2, "3 урок (2 пара)", "10:05", "10:50", breakAfterMinutes = 10),
        BellItem(4, 2, "4 урок (2 пара)", "11:00", "11:45", breakAfterMinutes = 10),
        BellItem(5, 3, "5 урок (3 пара)", "11:55", "12:40", breakAfterMinutes = 10),
        BellItem(6, 3, "6 урок (3 пара)", "12:50", "13:35", breakAfterMinutes = 10),
        BellItem(7, 4, "7 урок (4 пара)", "13:45", "14:30", breakAfterMinutes = 10),
        BellItem(8, 4, "8 урок (4 пара)", "14:40", "15:25", breakAfterMinutes = 0)
    )

    // Обратная совместимость для существующего кода
    val BELLS: List<BellItem> = STANDARD_BELLS

    /**
     * Возвращает тип расписания для дня недели (1 = Пн .. 6 = Сб, 7 = Вс).
     */
    fun getTypeForDay(dayOfWeek: Int): BellScheduleType {
        return when (dayOfWeek) {
            4 -> BellScheduleType.THURSDAY
            6 -> BellScheduleType.SATURDAY
            else -> BellScheduleType.STANDARD
        }
    }

    /**
     * Возвращает список элементов звонков для указанного типа.
     */
    fun getBellsForType(type: BellScheduleType): List<BellItem> {
        return when (type) {
            BellScheduleType.STANDARD -> STANDARD_BELLS
            BellScheduleType.THURSDAY -> THURSDAY_BELLS
            BellScheduleType.SATURDAY -> SATURDAY_BELLS
        }
    }

    /**
     * Возвращает список элементов звонков для дня недели (1..7).
     */
    fun getBellsForDay(dayOfWeek: Int): List<BellItem> {
        return getBellsForType(getTypeForDay(dayOfWeek))
    }

    /**
     * Возвращает текущий активный слот (урок или инфочас) по минутам от начала суток.
     */
    fun getCurrentSlot(currentMinutes: Int, dayOfWeek: Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)): BellItem? {
        val convertedDay = when (dayOfWeek) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
        val bells = getBellsForDay(convertedDay)
        return bells.firstOrNull { currentMinutes in it.startMinutes..it.endMinutes }
    }

    /**
     * Возвращает время пары (1..6) или урока (1..12) для указанного дня недели.
     */
    fun getTimeForNumber(number: Int, dayOfWeek: Int = 1): Pair<String, String> {
        val bells = getBellsForDay(dayOfWeek)
        // Сначала пробуем как номер пары (1..6)
        if (number in 1..6) {
            val pairLessons = bells.filter { it.pairNumber == number }
            if (pairLessons.isNotEmpty()) {
                val start = pairLessons.first().start
                val end = pairLessons.last().end
                return Pair(start, end)
            }
        }
        // Если как номер урока (1..12)
        val lesson = bells.find { it.lessonNumber == number }
        if (lesson != null) {
            return Pair(lesson.start, lesson.end)
        }
        return Pair("08:15", "09:55")
    }

    fun getBellForNumber(number: Int): BellItem? {
        return STANDARD_BELLS.firstOrNull { it.lessonNumber == number || it.pairNumber == number }
    }

    /**
     * Возвращает время начала и конца УРОКА (1..12) для указанного дня недели.
     * Документы сайта guo-mpk.by нумеруют занятия по урокам (не по парам),
     * поэтому парсер и виджеты используют именно этот метод.
     */
    fun getTimeForLessonNumber(number: Int, dayOfWeek: Int = 1): Pair<String, String> {
        val lesson = getBellsForDay(dayOfWeek).find { it.lessonNumber == number }
        if (lesson != null) {
            return Pair(lesson.start, lesson.end)
        }
        return Pair("08:15", "09:00")
    }
}
