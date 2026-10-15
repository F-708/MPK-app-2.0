package com.politex.update

import android.content.Context
import com.politex.BuildConfig
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val notes: String,
)

object AppUpdateChecker {
    private val client by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    internal fun parse(
        json: String?,
        flavor: String,
        currentCode: Int,
    ): UpdateInfo? {
        if (json.isNullOrBlank()) return null
        val entry = runCatching { JSONObject(json).optJSONObject(flavor) }.getOrNull() ?: return null
        val code = entry.optInt("versionCode")
        val url = entry.optString("url")
        if (code <= currentCode || url.isBlank()) return null
        return UpdateInfo(
            versionCode = code,
            versionName = entry.optString("versionName"),
            url = url,
            notes = entry.optString("notes"),
        )
    }

    fun check(context: Context): UpdateInfo? {
        if (!UPDATE_ENABLED_FLAVORS.contains(BuildConfig.FLAVOR)) return null
        return runCatching {
            val request = Request.Builder().url(BuildConfig.UPDATE_MANIFEST_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                parse(response.body?.string(), BuildConfig.FLAVOR, BuildConfig.VERSION_CODE)
            }
        }.getOrNull()
    }

    internal val UPDATE_ENABLED_FLAVORS = setOf("standard")
}
