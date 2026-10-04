package com.politex.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * R1-СЖАТИЕ: общий формат dd.MM.yyyy (был продублирован в 8 местах data-слоя).
 * SimpleDateFormat не потокобезопасен — создаём на каждый вызов, как и раньше.
 */
object DateFormats {
    fun dotDate(cal: Calendar): String = dotDate(cal.time)

    fun dotDate(date: Date): String = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).format(date)
}
