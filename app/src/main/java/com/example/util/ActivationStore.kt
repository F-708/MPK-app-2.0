package com.example.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Факт активации приложения.
 *
 * Хранится в SharedPreferences, поэтому:
 * - переживает обновление приложения (данные не трогаются);
 * - слетает при переустановке — код придётся ввести заново.
 *
 * Чтобы «слетало при переустановке» действительно работало, в манифесте должно
 * стоять android:allowBackup="false": иначе Android может восстановить настройки
 * из облачной копии и активация вернётся сама.
 */
object ActivationStore {

    private const val PREFS_NAME = "mpk_activation"
    private const val KEY_ACTIVATED_AT = "activated_at"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isActivated(context: Context): Boolean =
        prefs(context).getLong(KEY_ACTIVATED_AT, 0L) > 0L

    /** Момент активации в миллисекундах (0 — не активировано). */
    fun activatedAt(context: Context): Long = prefs(context).getLong(KEY_ACTIVATED_AT, 0L)

    fun activate(context: Context, nowMs: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_ACTIVATED_AT, nowMs).apply()
    }
}
