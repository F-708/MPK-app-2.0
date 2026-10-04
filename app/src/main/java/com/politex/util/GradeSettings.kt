package com.politex.util

import android.content.Context

object GradeSettings {
    private const val PREFS = "mpk_prefs"
    private const val KEY_WEIGHTS_GLOBAL = "grades_weights_global"

    fun getGlobalWeightsEnabled(context: Context): Boolean =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_WEIGHTS_GLOBAL, true)

    fun setGlobalWeightsEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WEIGHTS_GLOBAL, enabled)
            .apply()
    }
}
