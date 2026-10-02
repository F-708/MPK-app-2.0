package com.politex.util

import java.util.Calendar

/**
 * E3: единая конвертация Calendar → день недели расписания (1=Пн..6=Сб, 7=Вс).
 * Раньше этот when-блок был продублирован 18 раз в 12 файлах (виджеты, нотификатор,
 * воркер, карта, экраны) — расхождение копий стало бы скрытым багом дат.
 * Канонический impl; MpkScheduleParser.dowOfCalendar делегирует сюда.
 */
object Dow {
    fun of(cal: Calendar): Int = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        else -> 7
    }
}
