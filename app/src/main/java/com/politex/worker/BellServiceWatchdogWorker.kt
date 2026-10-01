package com.politex.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.politex.util.BellCountdownNotifier
import com.politex.util.BellTimerService
import com.politex.widget.BellCountdownWidgetProvider

/**
 * WID-02/BEL-03: страховка от убийства BellTimerService агрессивными OEM (Xiaomi/Samsung
 * battery saver убивают даже foreground specialUse-сервис). START_STICKY перезапуска не
 * гарантирует, точный будильник на Android 14+ по умолчанию не выдан — поэтому раз в ~15 мин
 * воркер проверяет: строка отсчёта включена, а сервис мёртв → поднять его. Заодно обновляет
 * минутные виджеты, чтобы они не «залипали» на старом времени.
 */
class BellServiceWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (BellCountdownNotifier.isEnabled(ctx) && !BellTimerService.isRunning) {
            BellTimerService.start(ctx)
        }
        // Даже если сервис жив, виджеты могли пропустить тик при doze — дёргаем минутные.
        BellCountdownWidgetProvider.updateAll(ctx)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "mpk_bell_service_watchdog"
    }
}
