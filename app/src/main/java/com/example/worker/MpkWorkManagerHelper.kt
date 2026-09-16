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

    /**
     * Как часто проверять, не выложили ли расписание на следующий учебный день.
     *
     * 15 минут — это МИНИМУМ, который допускает WorkManager: более частый
     * периодический опрос система просто не примет. Раз в 5 или 10 минут
     * штатными средствами нельзя — либо терять батарею на постоянном сервисе,
     * либо мириться с 15 минутами.
     */
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

            // UPDATE, а не KEEP: с KEEP уже поставленная задача остаётся со старым
            // интервалом навсегда, и смена частоты не доезжает до пользователей,
            // которые просто обновили приложение.
            workManager.enqueueUniquePeriodicWork(
                ScheduleCheckWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        } else {
            workManager.cancelUniqueWork(ScheduleCheckWorker.WORK_NAME)
        }
    }
}
