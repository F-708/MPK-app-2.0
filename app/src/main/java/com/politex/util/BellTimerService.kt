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
                    // Посекундный режим: секунды включены в одном из countdown-виджетов
                    // (кэш 60 с) ИЛИ стиль уведомления «с секундами». Иначе — минутный тик,
                    // поведение не меняется вообще.
                    // ponytail: посекундно тикает ТОЛЬКО foreground BellTimerService; WidgetAlarm
                    // остаётся минутной страховкой. Сервис убит OEM / не включён — секунды
                    // пропадают, виджет честно деградирует до минутного (посекундный будильник
                    // не заводим: будить процесс 60 раз в минуту ради смены цифры — слишком).
                    val style = BellCountdownNotifier.getStyle(this@BellTimerService)
                    val secondMode =
                        WidgetUpdateHelper.needSecondTick(
                            WidgetUpdateHelper.hasSecondsWidgets(this@BellTimerService),
                            style,
                        )
                    val nowMs = System.currentTimeMillis()
                    // тик к границе секунды (секунды) или минуты (как раньше)
                    delay(if (secondMode) 1000L - nowMs % 1000L else 60_000L - nowMs % 60_000L)

                    // Минутная граница: в секундном режиме просыпаемся каждый тик на :00
                    // секунды — тогда делаем и обычную минутную работу (updateAll).
                    val minuteBoundary = !secondMode || System.currentTimeMillis() % 60_000L < 1_000L

                    // Т10: показ ИЛИ демоут в заглушку — foreground-уведомление сервиса
                    // всегда обновляется через startForeground (в скрытом состоянии это
                    // заменяет строку тихой заглушкой; сервис живёт и в 07:00 вернёт отсчёт).
                    // Секунды в уведомлении = стиль WITH_SECONDS → пересборка каждый тик.
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
                        // только countdown-виджеты с включёнными секундами
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
        /** O6: сервис держит foreground-уведомление — нотификатор по этому флагу понимает,
         *  что снимать строку нельзя, можно только заменить её тихой заглушкой. */
        @Volatile
        var isRunning: Boolean = false

        /**
         * QA#1: возвращает true, если старт принял вызов. На Android 12+ старт FGS из
         * фона бросает ForegroundServiceStartNotAllowedException — раньше он тонул в
         * пустом catch, и страховка (watchdog) считала сервис поднятым. Теперь вызывающий
         * получает false и заводит точный будильник (см. BellServiceWatchdogWorker).
         */
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
