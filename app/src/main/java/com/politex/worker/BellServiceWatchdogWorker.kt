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

/**
 * WID-02/BEL-03: страховка от убийства BellTimerService агрессивными OEM (Xiaomi/Samsung
 * battery saver убивают даже foreground specialUse-сервис). START_STICKY перезапуска не
 * гарантирует, точный будильник на Android 14+ по умолчанию не выдан — поэтому раз в ~15 мин
 * воркер проверяет: строка отсчёта включена, а сервис мёртв → поднять его. Заодно каждые
 * ~15 мин освежает ВСЕ виджеты (X29), чтобы они не «залипали» на старом времени/дне.
 *
 * QA#1: на Android 12+ [BellTimerService.start] из фонового воркера бросает
 * ForegroundServiceStartNotAllowedException и возвращает false — прямой старт FGS из
 * WorkManager запрещён. Раньше исключение тонуло в пустом catch и doWork=success, т.е.
 * страховка не работала ровно в том кейсе, ради которого написана. Теперь при
 * started=false планируется точный будильник AlarmManager на ближайшее начало минуты:
 * broadcast PendingIntent попадает в [BellServiceAlarmReceiver], а срабатывание
 * будильника — разрешённый контекст для старта FGS (exemption «app receives a
 * PendingIntent from an (in)exact alarm»).
 */
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
        // X29(в): каждые ~15 мин гарантированно освежаем ВСЕ четыре виджета независимо от
        // того, жив сервис или мёртв. Раньше дёргали только countdown — Today/Bells ждали
        // свой onUpdate (30 мин) и залипали на старом времени/дне, если он не приходил.
        WidgetUpdateHelper.updateAllWidgets(ctx)
        return Result.success()
    }

    /** Будильник на ближайшее начало минуты; exact — если разрешение выдано, иначе inexact while-idle. */
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

        /**
         * QA#1, чистая логика для юнит-теста: будильник-страховка нужен, только если старт
         * FGS не удался И платформа вообще ограничивает фоновый старт (Android 8+,
         * ForegroundServiceStartNotAllowedException — Android 12+). На API < 26 start()
         * из воркера разрешён, false означает иную поломку — будильник её не починит.
         */
        internal fun shouldScheduleFallback(startFailed: Boolean, sdkInt: Int): Boolean =
            startFailed && sdkInt >= Build.VERSION_CODES.O
    }
}
