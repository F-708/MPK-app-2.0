package com.politex.widget

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * X43: смена даты/времени/часового пояса системы перепланирует виджеты и будильники.
 * Проверяем (1) ресивер объявлен в манифесте на всех трёх системных action'ах и
 * (2) делегат onTimeChanged отрабатывает на реальном контексте без исключений.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimeChangedReceiverTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun isRegisteredFor(action: String): Boolean {
        val name = TimeChangedReceiver::class.java.name
        return ctx.packageManager
            .queryBroadcastReceivers(Intent(action), 0)
            .any { it.activityInfo?.name == name }
    }

    @Test
    fun `ресивер объявлен в манифесте на смену времени, даты и часового пояса`() {
        assertTrue("ACTION_TIME_CHANGED", isRegisteredFor(Intent.ACTION_TIME_CHANGED))
        assertTrue("ACTION_DATE_CHANGED", isRegisteredFor(Intent.ACTION_DATE_CHANGED))
        assertTrue("ACTION_TIMEZONE_CHANGED", isRegisteredFor(Intent.ACTION_TIMEZONE_CHANGED))
    }

    @Test
    fun `onTimeChanged перепланирует без исключений`() {
        // Нет виджетов на «экране» → update* это no-op; тик-сервис выключён; WorkManager
        // обёрнут в try/catch. Достаточно, что делегат не бросает (X43).
        TimeChangedReceiver.onTimeChanged(ctx)
    }
}
