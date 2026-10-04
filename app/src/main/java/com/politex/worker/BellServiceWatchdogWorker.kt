package com.politex.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.politex.util.BellCountdownNotifier
import com.politex.util.BellTimerService
import com.politex.widget.WidgetUpdateHelper

class BellServiceWatchdogWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (BellCountdownNotifier.isEnabled(ctx) && !BellTimerService.isRunning) {
            val started = BellTimerService.start(ctx)
            if (shouldScheduleFallback(startFailed = !started, sdkInt = Build.VERSION.SDK_INT)) {
                scheduleFallbackAlarm(ctx)
            }
        }

        WidgetUpdateHelper.updateAllWidgets(ctx)
        return Result.success()
    }

    private fun scheduleFallbackAlarm(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx,
            FALLBACK_REQUEST_CODE,
            Intent(ctx, BellServiceAlarmReceiver::class.java).setAction(BellServiceAlarmReceiver.ACTION_START_SERVICE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val now = System.currentTimeMillis()
        val triggerAt = now + (60_000L - now % 60_000L)
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

    companion object {
        const val WORK_NAME = "mpk_bell_service_watchdog"
        private const val FALLBACK_REQUEST_CODE = 0xBE11

        internal fun shouldScheduleFallback(startFailed: Boolean, sdkInt: Int): Boolean =
            startFailed && sdkInt >= Build.VERSION_CODES.O
    }
}
