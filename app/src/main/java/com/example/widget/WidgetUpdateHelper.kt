package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Хелпер централизованного обновления виджетов рабочего стола МГПК.
 * 
 * КРИТИЧЕСКИЕ ПРАВИЛА:
 * 1. Offline-First: читает данные СТРОГО из локальной базы данных Room.
 * 2. Вызывается после каждой успешной синхронизации расписания и при смене активной группы.
 * 3. Не блокирует UI поток лаунчера.
 */
object WidgetUpdateHelper {

    private const val PREFS_NAME = "mpk_widget_prefs"
    private const val KEY_SELECTED_GROUP = "selected_group"
    private const val KEY_NOTIFY_NEXT_DAY = "notify_next_day_schedule"

    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSelectedGroup(context: Context): String {
        return getPrefs(context).getString(KEY_SELECTED_GROUP, "41О") ?: "41О"
    }

    fun setSelectedGroup(context: Context, groupName: String) {
        getPrefs(context).edit().putString(KEY_SELECTED_GROUP, groupName).apply()
        updateAllWidgets(context)
    }

    fun isNotificationEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIFY_NEXT_DAY, true)
    }

    fun setNotificationEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NOTIFY_NEXT_DAY, enabled).apply()
    }

    /**
     * Обновляет все экземпляры 2x2 и 4x2 виджетов.
     */
    fun updateAllWidgets(context: Context) {
        widgetScope.launch {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return@launch

            // 1. Обновляем LiveLessonWidget (2x2)
            val liveLessonComponent = ComponentName(context, LiveLessonWidgetProvider::class.java)
            val liveIds = appWidgetManager.getAppWidgetIds(liveLessonComponent)
            if (liveIds != null && liveIds.isNotEmpty()) {
                LiveLessonWidgetProvider.updateAppWidgets(context, appWidgetManager, liveIds)
            }

            // 2. Обновляем ScheduleWidget (4x2)
            val scheduleComponent = ComponentName(context, ScheduleWidgetProvider::class.java)
            val scheduleIds = appWidgetManager.getAppWidgetIds(scheduleComponent)
            if (scheduleIds != null && scheduleIds.isNotEmpty()) {
                ScheduleWidgetProvider.updateAppWidgets(context, appWidgetManager, scheduleIds)
            }
        }
    }
}
