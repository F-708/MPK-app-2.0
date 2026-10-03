package com.politex.worker

import android.content.Context
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
 */
class BellServiceWatchdogWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (BellCountdownNotifier.isEnabled(ctx) && !BellTimerService.isRunning) {
            BellTimerService.start(ctx)
        }
        // X29(в): каждые ~15 мин гарантированно освежаем ВСЕ четыре виджета независимо от
        // того, жив сервис или мёртв. Раньше дёргали только countdown — Today/Bells ждали
        // свой onUpdate (30 мин) и залипали на старом времени/дне, если он не приходил.
        WidgetUpdateHelper.updateAllWidgets(ctx)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "mpk_bell_service_watchdog"
    }
}
