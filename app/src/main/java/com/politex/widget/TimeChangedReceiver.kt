package com.politex.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.politex.util.BellCountdownNotifier
import com.politex.util.BellTimerService
import com.politex.worker.MpkWorkManagerHelper

/**
 * X43: пользователь сменил дату/время/часовой пояс — виджеты и будильники зависают в
 * старом состоянии: setExact заведён на прежнее абсолютное время (не сработает вовремя),
 * отсчёт считает по устаревшим минутам, Today показывает вчерашний день. AppWidgetProvider
 * ловит только widget-экшены, поэтому нужен ОТДЕЛЬНЫЙ ресивер на системные broadcasts
 * (они в списке исключений фонового ограничения — манифест-ресивер их получает, API 26+).
 *
 * Реакция: пересобрать все 4 виджета, перепланировать минутный будильник, перезапустить
 * тик-сервис (если строка отсчёта включена) и перепланировать WorkManager-воркеры.
 *
 * Примечание: ACTION_DATE_CHANGED платформенно ненадёжен (баг трекера 36908018 — не
 * рассылается при переводе даты назад), но ручная смена времени/даты всегда шлёт
 * ACTION_TIME_CHANGED, которым этот сценарий и ловится.
 */
class TimeChangedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        when (intent?.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> onTimeChanged(context)
        }
    }

    companion object {
        /**
         * Вынесено из onReceive для юнит-теста (X43). Всё обёрнуто так, чтобы ресивер
         * не падал: процесс может быть поднят системой без инициализации WorkManager.
         */
        internal fun onTimeChanged(context: Context) {
            // 1) пересобрать все 4 виджета (NowNext / Today / Bells / Countdown)
            WidgetUpdateHelper.updateAllWidgets(context)
            // 2) минутный будильник: прежний setExact на старое время не сработает — перепланировать
            WidgetAlarm.scheduleNext(context)
            // 3) тик-сервис, если строка отсчёта включена: перезапуск пересчитает минуты/секунды
            if (BellCountdownNotifier.isEnabled(context)) {
                BellTimerService.start(context)
            }
            // 4) WorkManager-воркеры перепланировать. try/catch — WorkManager может быть не
            //    инициализирован, если процесс поднят одним лишь этим ресивером (не критично:
            //    периодические воркеты и так переживут смену времени, это лишь страховка).
            try {
                MpkWorkManagerHelper.setupPeriodicScheduleCheck(context)
                MpkWorkManagerHelper.setupBellServiceWatchdog(context)
                MpkWorkManagerHelper.setupPeriodicReminderCheck(context)
            } catch (_: Exception) {
            }
        }
    }
}
