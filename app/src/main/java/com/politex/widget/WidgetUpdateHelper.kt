package com.politex.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object WidgetUpdateHelper {
    private const val PREFS_NAME = "mpk_widget_prefs"
    private const val KEY_SELECTED_GROUP = "selected_group"
    private const val KEY_NOTIFY_NEXT_DAY = "notify_next_day_schedule"

    // X29: отметка последнего ПОЛНОГО update всех четырёх виджетов. Минутный тик
    // обновляет только countdown/NowNext, а Today/Bells живут на onUpdate (30 мин) и
    // воркере (15 мин). Если onUpdate не пришёл (doze/OEM), минутный тик по этой метке
    // понимает, что виджеты протухли, и форсирует полную пересборку.
    private const val KEY_LAST_FULL_UPDATE = "last_full_update_ms"
    private const val STALE_AFTER_MS = 10 * 60_000L

    // QA#2: общий IO-scope виджетов — в нём провайдеры/ресиверы пересобирают виджеты
    // после goAsync(), чтобы runBlocking-чтение Room не попадало на main thread.
    internal val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        // QA#2: вызов приходит с main (AppViewModel.setGroup, импорт бэкапа) —
        // пересборка виджетов читает Room, уходим на IO.
        widgetScope.launch { updateAllWidgets(context) }
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
        // X29: полный update состоялся — сбросить отметку протухания
        getPrefs(context).edit().putLong(KEY_LAST_FULL_UPDATE, System.currentTimeMillis()).apply()
    }

    /**
     * X29: последний ПОЛНЫЙ update всех виджетов был старше [STALE_AFTER_MS] (или его вовсе
     * не было). Значит Today/Bells могли пропустить свой onUpdate — минутный тик форсирует
     * полную пересборку. Чистое чтение prefs, без IPC.
     */
    fun isFullUpdateStale(context: Context): Boolean {
        val last = getPrefs(context).getLong(KEY_LAST_FULL_UPDATE, 0L)
        return System.currentTimeMillis() - last > STALE_AFTER_MS
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
