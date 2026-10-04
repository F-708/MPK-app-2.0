package com.politex.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.widget.RemoteViews
import com.politex.MainActivity
import com.politex.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object WidgetUpdateHelper {
    private const val PREFS_NAME = "mpk_widget_prefs"
    private const val KEY_SELECTED_GROUP = "selected_group"
    private const val KEY_NOTIFY_NEXT_DAY = "notify_next_day_schedule"

    private const val KEY_LAST_FULL_UPDATE = "last_full_update_ms"
    private const val STALE_AFTER_MS = 10 * 60_000L

    internal val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var secondsWidgetsCache = false

    @Volatile private var secondsCacheAt = 0L

    fun needSecondTick(
        secondsWidgetEnabled: Boolean,
        notificationStyle: com.politex.util.BellCountdownNotifier.Style,
    ): Boolean = secondsWidgetEnabled || notificationStyle == com.politex.util.BellCountdownNotifier.Style.WITH_SECONDS

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

        getPrefs(context).edit().putLong(KEY_LAST_FULL_UPDATE, System.currentTimeMillis()).apply()
    }

    fun isFullUpdateStale(context: Context): Boolean {
        val last = getPrefs(context).getLong(KEY_LAST_FULL_UPDATE, 0L)
        return System.currentTimeMillis() - last > STALE_AFTER_MS
    }

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

    // D1: общий «exact если можно, иначе inexact while-idle» для всех будильников приложения.
    // Используется в WidgetAlarm.scheduleNext, BellTimerService.scheduleFallbackAlarm.
    fun setExactOrIdle(
        context: Context,
        triggerAt: Long,
        pi: PendingIntent,
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
        }
    }

    // D3: единый PendingIntent «открыть приложение» для всех виджетов (тап по корню/чипу).
    // code — из неймспейсов SRED11, extras собираются только когда нужны (семантика прежняя).
    fun openAppIntent(
        context: Context,
        code: Int,
        tab: String,
        scrollTarget: String? = null,
        room: String? = null,
        showRoute: Boolean = false,
        fromRoom: String? = null,
    ): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("EXTRA_OPEN_TAB", tab)
        if (scrollTarget != null) intent.putExtra("EXTRA_SCROLL_TARGET", scrollTarget)
        if (room != null) intent.putExtra("EXTRA_ROOM", room)
        if (showRoute) intent.putExtra("EXTRA_SHOW_ROUTE", true)
        if (!fromRoom.isNullOrBlank()) intent.putExtra("EXTRA_FROM_ROOM", fromRoom)
        return PendingIntent.getActivity(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // D5: показать idle-колокольчик (AVD) в пустом состоянии виджета.
    fun showIdle(
        views: RemoteViews,
        id: Int = R.id.iv_idle,
    ) {
        views.setImageViewResource(id, R.drawable.anim_idle)
        views.setViewVisibility(id, android.view.View.VISIBLE)
    }
}

// SRED9: очистка per-widget настроек при удалении виджета, иначе ключи текут навсегда.
object WidgetPrefs {
    private val PREFIXES = listOf("widget_style_", "countdown_mode_", "widget_show_room_", "widget_seconds_")

    fun clear(
        context: Context,
        appWidgetId: Int,
    ) {
        val editor = WidgetUpdateHelper.getPrefs(context).edit()
        PREFIXES.forEach { editor.remove(it + appWidgetId) }
        editor.apply()
    }
}
