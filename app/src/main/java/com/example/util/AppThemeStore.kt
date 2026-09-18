package com.example.util

import android.content.Context
import com.example.ui.theme.AppTheme
import com.example.ui.theme.applyAppTheme

object AppThemeStore {

    private const val PREFS_NAME = "mpk_ui_prefs"
    private const val KEY_THEME = "app_theme"

    fun load(context: Context): AppTheme =
        AppTheme.from(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_THEME, null)
        )

    fun save(context: Context, theme: AppTheme) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme.id)
            .apply()
        applyAppTheme(theme)
    }
}
