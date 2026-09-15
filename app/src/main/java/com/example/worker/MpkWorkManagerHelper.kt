package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.widget.WidgetUpdateHelper
import java.util.concurrent.TimeUnit

/**
 * Менеджер планирования фоновых проверок расписания через WorkManager.
 */
object MpkWorkManagerHelper {

    fun setupPeriodicScheduleCheck(context: Context) {
        val isEnabled = WidgetUpdateHelper.isNotificationEnabled(context)
        val workManager = WorkManager.getInstance(context)

        if (isEnabled) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ScheduleCheckWorker>(45, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                ScheduleCheckWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } else {
            workManager.cancelUniqueWork(ScheduleCheckWorker.WORK_NAME)
        }
    }
}
