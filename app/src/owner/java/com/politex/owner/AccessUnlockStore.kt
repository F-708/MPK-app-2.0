package com.politex.owner

import android.content.Context

object AccessUnlockStore {
    private const val PREFS_FILE = "admin_access"
    private const val KEY_UNLOCKED = "access_unlocked"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun isUnlocked(context: Context): Boolean = prefs(context).getBoolean(KEY_UNLOCKED, false)

    fun unlock(context: Context) {
        prefs(context)
            .edit()
            .putBoolean(KEY_UNLOCKED, true)
            .apply()
    }

    fun reset(context: Context) {
        prefs(context)
            .edit()
            .remove(KEY_UNLOCKED)
            .apply()
    }
}
