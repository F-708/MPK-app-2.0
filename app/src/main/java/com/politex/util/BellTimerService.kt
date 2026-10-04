package com.politex.util

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.politex.widget.BellCountdownWidgetProvider
import com.politex.widget.WidgetUpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BellTimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        BellCountdownNotifier.createChannel(this)

        startForeground(BellCountdownNotifier.NOTIFICATION_ID, BellCountdownNotifier.buildNotification(this))
        isRunning = true
        startTicking()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (tickJob?.isActive != true) startTicking()
        return START_STICKY
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob =
            scope.launch {
                while (true) {

                    val style = BellCountdownNotifier.getStyle(this@BellTimerService)
                    val secondMode =
                        WidgetUpdateHelper.needSecondTick(
                            WidgetUpdateHelper.hasSecondsWidgets(this@BellTimerService),
                            style,
                        )
                    val nowMs = System.currentTimeMillis()

                    delay(if (secondMode) 1000L - nowMs % 1000L else 60_000L - nowMs % 60_000L)

                    val minuteBoundary = !secondMode || System.currentTimeMillis() % 60_000L < 1_000L

                    if (minuteBoundary || style == BellCountdownNotifier.Style.WITH_SECONDS) {
                        try {
                            startForeground(
                                BellCountdownNotifier.NOTIFICATION_ID,
                                BellCountdownNotifier.buildNotification(this@BellTimerService),
                            )
                        } catch (_: Exception) {
                            BellCountdownNotifier.show(this@BellTimerService)
                        }
                    }

                    if (secondMode) {

                        BellCountdownWidgetProvider.updateSecondsWidgets(this@BellTimerService)
                    }

                    if (minuteBoundary) {
                        BellCountdownWidgetProvider.updateAll(this@BellTimerService)
                    }
                }
            }
    }

    override fun onDestroy() {
        tickJob?.cancel()
        isRunning = false
        super.onDestroy()
    }

    companion object {

        @Volatile
        var isRunning: Boolean = false

        fun start(context: Context): Boolean {
            val intent = Intent(context, BellTimerService::class.java)
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (e: Exception) {
                val fgsBlocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    e is ForegroundServiceStartNotAllowedException
                android.util.Log.w(
                    "BellTimerService",
                    if (fgsBlocked) "FGS start not allowed from background — caller should schedule fallback alarm" else "start failed",
                    e,
                )
                false
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
