package com.politex.worker

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.politex.util.BellCountdownNotifier
import com.politex.util.BellTimerService
import com.politex.widget.WidgetAlarm
import com.politex.widget.WidgetUpdateHelper

class BellServiceWatchdogWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (BellCountdownNotifier.isEnabled(ctx) && !BellTimerService.isRunning) {
            val started = BellTimerService.start(ctx)
            // SRED8/D1: fallback-будильник теперь общий и живёт в BellTimerService.
            if (shouldScheduleFallback(startFailed = !started, sdkInt = Build.VERSION.SDK_INT)) {
                BellTimerService.scheduleFallbackAlarm(ctx)
            }
        }

        // RC2 (v2.0): минутный будильник мог быть дропнут (force-stop/restricted bucket,
        // деградация exact-аларма на Android 12+) — watchdog раз в 15 мин его перевзводит.
        runCatching { WidgetAlarm.scheduleNext(ctx) }

        // RC1 (v2.0): уроки сегодняшней даты могли не успеть синхронизироваться —
        // fallback-проверка (основной вызов живёт в минутном TICK).
        runCatching { WidgetUpdateHelper.ensureTodaySchedule(ctx) }

        WidgetUpdateHelper.updateAllWidgets(ctx)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "mpk_bell_service_watchdog"

        internal fun shouldScheduleFallback(startFailed: Boolean, sdkInt: Int): Boolean =
            startFailed && sdkInt >= Build.VERSION_CODES.O
    }
}
