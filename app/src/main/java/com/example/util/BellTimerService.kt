package com.example.util

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.example.widget.BellCountdownWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Служба минутного обновления строки «До звонка» и виджета.
 *
 * Почему служба, а не AlarmManager: системный будильник душится режимом
 * энергосбережения (Doze) и агрессивными оболочками производителей —
 * отсчёт «застывал» на несколько минут. Служба с постоянным уведомлением
 * даёт ровный такт, привязанный к границе минуты устройства.
 *
 * Работает ТОЛЬКО пока включена постоянная строка в настройках, и живёт
 * ровно столько же — никаких фоновых процессов без ведома пользователя.
 */
class BellTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null
    private var widgetRefreshCounter = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        BellCountdownNotifier.createChannel(this)
        // Сразу показываем уведомление, чтобы служба не была убита
        startForeground(BellCountdownNotifier.NOTIFICATION_ID, BellCountdownNotifier.buildNotification(this))
        startTicking()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Перезапуск после сбоя — снова в работу
        if (tickJob?.isActive != true) startTicking()
        return START_STICKY
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {
                // Такт привязан к границе минуты устройства: в 12:34:00, 12:35:00 и т. д.
                val nowMs = System.currentTimeMillis()
                val msToNextMinute = 60_000L - (nowMs % 60_000L)
                delay(msToNextMinute)

                BellCountdownNotifier.show(this@BellTimerService)

                // Виджет обновляем тем же тактом (если он есть на рабочем столе)
                widgetRefreshCounter++
                if (widgetRefreshCounter >= 1) {
                    widgetRefreshCounter = 0
                    BellCountdownWidgetProvider.updateAll(this@BellTimerService)
                }
            }
        }
    }

    override fun onDestroy() {
        tickJob?.cancel()
        super.onDestroy()
    }

    companion object {
        /** Запустить службу (идемпотентно). */
        fun start(context: Context) {
            val intent = Intent(context, BellTimerService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // Фоновый запуск запрещён системой — не критично, останется AlarmManager
            }
        }

        /** Остановить службу. */
        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, BellTimerService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
