package com.politex.weather

import java.util.Calendar

/**
 * Сезонное оформление эмбиент-слоя поверх погоды.
 *
 * Отдельно от осадков: снег идёт по прогнозу, а гирлянда/тыквы — по календарю.
 * Хэллоуин не отменяет дождь, Новый год не отменяет снег — декор рисуется поверх.
 */
enum class Season(val id: String) {
    NONE("none"),
    HALLOWEEN("halloween"),
    NEW_YEAR("new_year"),
    ;

    companion object {
        fun from(id: String?): Season = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/**
 * Активный сезон по дате.
 *
 * Окна выбраны по «вайбу», а не по точному дню:
 *  - Хэллоуин: 25 октября – 1 ноября (неделя до + сам праздник);
 *  - Новый год: 22 декабря – 8 января (когда уже снег и чувствуется приближение).
 *
 * Чистая функция от (месяц, день) — тестируется без Android.
 */
fun seasonForDate(month: Int, dayOfMonth: Int): Season = when {
    month == Calendar.OCTOBER && dayOfMonth >= 25 -> Season.HALLOWEEN
    month == Calendar.NOVEMBER && dayOfMonth <= 1 -> Season.HALLOWEEN
    month == Calendar.DECEMBER && dayOfMonth >= 22 -> Season.NEW_YEAR
    month == Calendar.JANUARY && dayOfMonth <= 8 -> Season.NEW_YEAR
    else -> Season.NONE
}

/** Сезон прямо сейчас по системным часам. */
fun currentSeason(now: Calendar = Calendar.getInstance()): Season =
    seasonForDate(now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
