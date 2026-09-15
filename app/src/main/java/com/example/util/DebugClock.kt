package com.example.util

import android.content.Context
import java.util.Calendar

/**
 * Часы приложения с дебаг-переопределением времени.
 *
 * В настройках (тумблер «Дебаг») можно задать фиксированные дату и время —
 * приложение (звонки, виджет «До звонка», подсветка текущего урока) будет
 * считать, что сейчас именно это время. Отключено — системное время.
 *
 * Хранится в SharedPreferences, чтобы виджет тоже видел override.
 */
object DebugClock {

    private const val PREF_DEBUG_TIME = "debug_time_millis"
    private const val PREF_DEBUG_ENABLED = "debug_time_enabled"

    /** Установить дебаг-время (null — сбросить). */
    fun setOverride(context: Context, timeMillis: Long?) {
        val prefs = context.getSharedPreferences("mpk_debug", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(PREF_DEBUG_ENABLED, timeMillis != null)
            .putLong(PREF_DEBUG_TIME, timeMillis ?: 0L)
            .apply()
    }

    fun isOverridden(context: Context): Boolean =
        context.getSharedPreferences("mpk_debug", Context.MODE_PRIVATE)
            .getBoolean(PREF_DEBUG_ENABLED, false)

    fun overrideMillis(context: Context): Long? =
        if (isOverridden(context)) {
            context.getSharedPreferences("mpk_debug", Context.MODE_PRIVATE)
                .getLong(PREF_DEBUG_TIME, 0L)
        } else null

    /** Текущее время с учётом дебаг-переопределения. */
    fun now(context: Context? = null): Calendar {
        val override = context?.let { overrideMillis(it) }
        return if (override != null) {
            Calendar.getInstance().apply { timeInMillis = override }
        } else {
            Calendar.getInstance()
        }
    }
}
