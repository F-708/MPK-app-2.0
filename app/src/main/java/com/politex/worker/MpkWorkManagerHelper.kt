package com.politex.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.politex.widget.WidgetUpdateHelper
import java.util.concurrent.TimeUnit

object MpkWorkManagerHelper {

    private const val CHECK_INTERVAL_MINUTES = 15L

    fun setupPeriodicScheduleCheck(context: Context) {
        val isEnabled = WidgetUpdateHelper.isNotificationEnabled(context)
        val workManager = WorkManager.getInstance(context)

        if (isEnabled) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ScheduleCheckWorker>(
                CHECK_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                ScheduleCheckWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        } else {
            workManager.cancelUniqueWork(ScheduleCheckWorker.WORK_NAME)
        }
    }

    /**
     * WID-02/BEL-03: периодическая страховка BellTimerService (OEM убивают FGS, START_STICKY
     * не гарантирует перезапуск, точный будильник на 14+ не выдан). Сеть не нужна — воркер
     * только поднимает сервис и обновляет минутные виджеты. Работает всегда, гейтится внутри
     * по isEnabled строки отсчёта.
     */
    fun setupBellServiceWatchdog(context: Context) {
        val request = PeriodicWorkRequestBuilder<BellServiceWatchdogWorker>(
            CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BellServiceWatchdogWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
