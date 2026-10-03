package com.politex.util

import android.content.Context

/**
 * V14: локальные флаги пройденности интерактивных туториалов.
 *
 * Заказчик: «флаги переживают обновление приложения; после апдейта показывается только
 * туториал НОВОЙ непройденной фичи». SharedPreferences хранятся в файле `mpk_tutorial`
 * в data-каталоге приложения — они НЕ стираются при установке нового APK поверх
 * (в отличие от кэша), поэтому однажды пройденный туториал больше не всплывает, а
 * добавленные в обновлении фичи (свои ключи) — показываются. Ключ — `tutorial_done_$feature`.
 */
object TutorialFlags {
    private const val PREFS = "mpk_tutorial"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isDone(ctx: Context, feature: String): Boolean = prefs(ctx).getBoolean(key(feature), false)

    fun markDone(ctx: Context, feature: String) {
        prefs(ctx).edit().putBoolean(key(feature), true).apply()
    }

    private fun key(feature: String) = "tutorial_done_$feature"
}
