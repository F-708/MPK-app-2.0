package com.politex.util

import android.content.Context
import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.applyAppTheme
import kotlinx.coroutines.launch

object AppThemeStore {
    private const val PREFS_NAME = "mpk_ui_prefs"
    private const val KEY_THEME = "app_theme"

    fun load(context: Context): AppTheme = AppTheme.from(
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, null),
    )

    fun save(
        context: Context,
        theme: AppTheme,
    ) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme.id)
            .apply()
        applyAppTheme(theme)

        com.politex.widget.WidgetUpdateHelper.widgetScope.launch {
            com.politex.widget.WidgetUpdateHelper.updateAllWidgets(context.applicationContext)
        }
    }
}
