package com.politex.util

import android.content.Context

object TutorialFlags {
    private const val PREFS = "mpk_tutorial"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isDone(ctx: Context, feature: String): Boolean = prefs(ctx).getBoolean(key(feature), false)

    fun markDone(ctx: Context, feature: String) {
        prefs(ctx).edit().putBoolean(key(feature), true).apply()
    }

    fun resetAll(ctx: Context) {
        val p = prefs(ctx)
        val editor = p.edit()
        p.all.keys.filter { it.startsWith("tutorial_done_") }.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun key(feature: String) = "tutorial_done_$feature"
}
