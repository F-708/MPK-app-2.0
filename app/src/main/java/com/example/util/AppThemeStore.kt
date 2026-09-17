package com.example.util

import android.content.Context
import com.example.ui.theme.AppTheme
import com.example.ui.theme.applyAppTheme

/**
 * Выбранная тема оформления приложения.
 *
 * Хранится отдельно от настроек виджетов: тема — про всё приложение,
 * а не про конкретный виджет на рабочем столе.
 */
object AppThemeStore {

    private const val PREFS_NAME = "mpk_ui_prefs"
    private const val KEY_THEME = "app_theme"

    fun load(context: Context): AppTheme =
        AppTheme.from(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_THEME, null)
        )

    /** Сохраняет и сразу применяет — экраны перекрашиваются без перезапуска. */
    fun save(context: Context, theme: AppTheme) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme.id)
            .apply()
        applyAppTheme(theme)
    }
}
