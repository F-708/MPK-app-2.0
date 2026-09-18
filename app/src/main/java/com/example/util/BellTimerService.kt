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

class BellTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null
    private var widgetRefreshCounter = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        BellCountdownNotifier.createChannel(this)

        startForeground(BellCountdownNotifier.NOTIFICATION_ID, BellCountdownNotifier.buildNotification(this))
        startTicking()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (tickJob?.isActive != true) startTicking()
        return START_STICKY
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {

                val nowMs = System.currentTimeMillis()
                val msToNextMinute = 60_000L - (nowMs % 60_000L)
                delay(msToNextMinute)

                BellCountdownNotifier.show(this@BellTimerService)

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

        fun start(context: Context) {
            val intent = Intent(context, BellTimerService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {

            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, BellTimerService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
