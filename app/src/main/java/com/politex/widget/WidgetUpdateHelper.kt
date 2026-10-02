package com.politex.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object WidgetUpdateHelper {
    private const val PREFS_NAME = "mpk_widget_prefs"
    private const val KEY_SELECTED_GROUP = "selected_group"
    private const val KEY_NOTIFY_NEXT_DAY = "notify_next_day_schedule"

    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Секунды в виджетах: кэш 60 с — проверка зовётся из цикла BellTimerService. */
    @Volatile private var secondsWidgetsCache = false

    @Volatile private var secondsCacheAt = 0L

    /**
     * Общий флаг посекундного режима: секунды хотя бы в одном countdown-виджете ИЛИ
     * стиль уведомления «с секундами». Чистая функция (юнит-тесты).
     */
    fun needSecondTick(
        secondsWidgetEnabled: Boolean,
        notificationStyle: com.politex.util.BellCountdownNotifier.Style,
    ): Boolean = secondsWidgetEnabled || notificationStyle == com.politex.util.BellCountdownNotifier.Style.WITH_SECONDS

    /** Есть ли на экране countdown-виджет с включёнными секундами (кэш 60 с). */
    fun hasSecondsWidgets(context: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - secondsCacheAt < 60_000L) return secondsWidgetsCache
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, BellCountdownWidgetProvider::class.java))
        val result = ids.any { WidgetOptions.seconds(context, it) }
        secondsWidgetsCache = result
        secondsCacheAt = now
        return result
    }

    /** Сброс кэша секунд (вызов после смены настройки виджета). */
    fun invalidateSecondsCache() {
        secondsCacheAt = 0L
    }

    fun getPrefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSelectedGroup(context: Context): String = getPrefs(context).getString(KEY_SELECTED_GROUP, "") ?: ""

    fun hasSelectedGroup(context: Context): Boolean = getPrefs(context).contains(KEY_SELECTED_GROUP)

    fun setSelectedGroup(
        context: Context,
        groupName: String,
    ) {
        getPrefs(context).edit().putString(KEY_SELECTED_GROUP, groupName).apply()
        updateAllWidgets(context)
    }

    fun isNotificationEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_NOTIFY_NEXT_DAY, true)

    fun setNotificationEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        getPrefs(context).edit().putBoolean(KEY_NOTIFY_NEXT_DAY, enabled).apply()
    }

    fun updateAllWidgets(context: Context) {
        BellCountdownWidgetProvider.updateAll(context)
        NowNextWidgetProvider.updateAll(context)
        TodayScheduleWidgetProvider.updateAll(context)
        BellsWidgetProvider.updateAll(context)
    }

    /**
     * O1: минутный тик — только виджеты с минутным отсчётом.
     * TodaySchedule/Bells меняются раз в урок, их обновляют onUpdate/WorkManager;
     * пересборка их каждую минуту давала лишний IPC и «мигание» виджета.
     */
    fun updateMinuteWidgets(context: Context) {
        BellCountdownWidgetProvider.updateAll(context)
        NowNextWidgetProvider.updateAll(context)
    }

    fun updateOne(
        context: Context,
        appWidgetId: Int,
    ) {
        when (WidgetKind.of(context, appWidgetId)) {
            WidgetKind.COUNTDOWN -> BellCountdownWidgetProvider.updateAll(context)
            WidgetKind.NOW_NEXT -> NowNextWidgetProvider.updateAll(context)
            WidgetKind.TODAY -> TodayScheduleWidgetProvider.updateAll(context)
            WidgetKind.BELLS -> BellsWidgetProvider.updateAll(context)
        }
    }
}
