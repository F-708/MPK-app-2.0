package com.politex.util

import android.content.Context

/**
 * GG3: предпочтение подгруппы пользователя для split-уроков (маршруты и виджеты).
 * NEUTRAL — приложение спрашивает/показывает обе; FIRST/SECOND — авто-выбор комнаты подгруппы.
 * Хранится в общих prefs виджетов, чтобы и виджеты и экраны читали один источник.
 */
enum class SubgroupPref {
    NEUTRAL,
    FIRST,
    SECOND,
    ;

    companion object {
        fun from(id: String?): SubgroupPref = entries.firstOrNull { it.name == id } ?: NEUTRAL
    }
}

object SubgroupSettings {
    private const val KEY = "subgroup_pref"

    fun get(context: Context): SubgroupPref =
        SubgroupPref.from(
            com.politex.widget.WidgetUpdateHelper.getPrefs(context).getString(KEY, null),
        )

    fun set(
        context: Context,
        pref: SubgroupPref,
    ) {
        com.politex.widget.WidgetUpdateHelper.getPrefs(context).edit().putString(KEY, pref.name).apply()
        // HH2: pref влияет на split-кнопки в NowNext+Today — перерисовать сразу, не ждать тика.
        com.politex.widget.WidgetUpdateHelper.refreshScheduleWidgets(context)
    }

    /** Индекс подгруппы для split-комнат (0=roomFirst, 1=roomSecond); null если NEUTRAL. */
    fun splitIndex(pref: SubgroupPref): Int? =
        when (pref) {
            SubgroupPref.FIRST -> 0
            SubgroupPref.SECOND -> 1
            SubgroupPref.NEUTRAL -> null
        }
}
