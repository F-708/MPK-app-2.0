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

    /**
     * Выбранная группа. Пустая строка — группа ещё не выбрана: раньше здесь
     * подставлялось «41О», и первый запуск выглядел так, будто разработчик из 41О.
     */
    fun getSelectedGroup(context: Context): String {
        return getPrefs(context).getString(KEY_SELECTED_GROUP, "") ?: ""
    }

    /** Выбирал ли пользователь группу (для показа обязательного диалога первого входа). */
    fun hasSelectedGroup(context: Context): Boolean {
        return getPrefs(context).contains(KEY_SELECTED_GROUP)
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
     * Обновляет все виджеты приложения: «До звонка», «Сейчас/дальше»
     * и остальные, которые появятся.
     */
    fun updateAllWidgets(context: Context) {
        BellCountdownWidgetProvider.updateAll(context)
        NowNextWidgetProvider.updateAll(context)
        TodayScheduleWidgetProvider.updateAll(context)
        BellsWidgetProvider.updateAll(context)
    }

    /**
     * Обновляет один конкретный виджет — нужен сразу после настройки, чтобы
     * пользователь увидел выбранную тему, не дожидаясь минутного такта.
     */
    fun updateOne(context: Context, appWidgetId: Int) {
        when (WidgetKind.of(context, appWidgetId)) {
            WidgetKind.COUNTDOWN -> BellCountdownWidgetProvider.updateAll(context)
            WidgetKind.NOW_NEXT -> NowNextWidgetProvider.updateAll(context)
            WidgetKind.TODAY -> TodayScheduleWidgetProvider.updateAll(context)
            WidgetKind.BELLS -> BellsWidgetProvider.updateAll(context)
        }
    }
}
