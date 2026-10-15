package com.politex.update

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

object UpdateDownloader {
    private val client by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    fun download(
        context: Context,
        url: String,
        versionCode: Int,
    ): File? {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "update-$versionCode.apk")
        return runCatching {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                target.outputStream().use { out -> body.byteStream().copyTo(out) }
                if (target.length() < MIN_APK_BYTES) {
                    target.delete()
                    return null
                }
                target
            }
        }.getOrNull()
    }

    internal fun apkIsValid(
        context: Context,
        file: File,
    ): Boolean {
        if (!file.exists() || file.length() < MIN_APK_BYTES) return false
        return runCatching {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                ?.packageName == context.packageName
        }.getOrDefault(false)
    }

    internal const val MIN_APK_BYTES = 1_000_000L
}
