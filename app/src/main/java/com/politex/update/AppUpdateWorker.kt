package com.politex.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.politex.R
import java.util.concurrent.TimeUnit

class AppUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {
    override fun doWork(): Result {
        val info = AppUpdateChecker.check(applicationContext) ?: return Result.success()
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_NOTIFIED, 0) >= info.versionCode) return Result.success()

        val file = UpdateDownloader.download(applicationContext, info.url, info.versionCode)
        if (file == null || !UpdateDownloader.apkIsValid(applicationContext, file)) {
            return Result.retry()
        }

        notifyReady(applicationContext, info)
        prefs.edit().putInt(KEY_NOTIFIED, info.versionCode).apply()
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "app_update_check"
        const val CHANNEL_ID = "mpk_app_updates"
        const val NOTIFICATION_ID = 2027
        private const val PREFS = "mpk_update_prefs"
        private const val KEY_NOTIFIED = "notified_version_code"

        fun schedule(context: Context) {
            if (!AppUpdateChecker.UPDATE_ENABLED_FLAVORS.contains(com.politex.BuildConfig.FLAVOR)) return
            val request =
                PeriodicWorkRequestBuilder<AppUpdateWorker>(12, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.UNMETERED)
                            .build(),
                    ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        private fun notifyReady(
            context: Context,
            info: UpdateInfo,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel =
                    NotificationChannel(
                        CHANNEL_ID,
                        "Обновления приложения",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    )
                context.getSystemService(NotificationManager::class.java)
                    ?.createNotificationChannel(channel)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val install =
                Intent(context, UpdateInstallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(UpdateInstallActivity.EXTRA_APK_PATH, apkFile(context, info.versionCode).absolutePath)
            val pending =
                PendingIntent.getActivity(
                    context,
                    NOTIFICATION_ID,
                    install,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val title = "Доступна версия ${info.versionName.ifBlank { info.versionCode.toString() }}"
            val body = info.notes.ifBlank { "Обновление «Мой Политех» загружено. Нажмите, чтобы установить." }
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_mpk_emblem)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .build()

            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
            }
        }

        internal fun apkFile(
            context: Context,
            versionCode: Int,
        ) = java.io.File(java.io.File(context.cacheDir, "updates"), "update-$versionCode.apk")
    }
}
