package com.politex

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.politex.ui.screens.MainScreen
import com.politex.ui.theme.MyApplicationTheme
import com.politex.ui.viewmodel.AppTab
import com.politex.ui.viewmodel.AppViewModel
import com.politex.util.NotificationHelper
import com.politex.worker.MpkWorkManagerHelper

class MainActivity : ComponentActivity() {

    private var mainViewModel: AppViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        com.politex.ui.theme.applyAppTheme(com.politex.util.AppThemeStore.load(this))

        NotificationHelper.createNotificationChannel(this)
        com.politex.util.BellCountdownNotifier.createChannel(this)

        if (com.politex.util.BellCountdownNotifier.isEnabled(this)) {
            com.politex.util.BellTimerService.start(this)
        }
        MpkWorkManagerHelper.setupPeriodicScheduleCheck(this)
        // WID-02/BEL-03: страховка FGS-сервиса отсчёта — OEM убивают его, воркер поднимает обратно
        MpkWorkManagerHelper.setupBellServiceWatchdog(this)
        // H1: доставка напоминаний о заданиях (периодический воркер, 15 мин)
        MpkWorkManagerHelper.setupPeriodicReminderCheck(this)

        // N16: холодный старт из виджета/уведомления — без splash, сразу к месту тапа
        val deepLink = shouldSkipSplash(
            intent?.hasExtra("EXTRA_OPEN_TAB") == true,
            intent?.hasExtra("EXTRA_ROOM") == true,
            intent?.hasExtra("EXTRA_SCROLL_TARGET") == true
        )

        setContent {
            val appViewModel: AppViewModel = viewModel()
            mainViewModel = appViewModel
            val uiState by appViewModel.uiState.collectAsState()

            LaunchedEffect(intent) {
                handleIntent(intent, appViewModel)
            }

            androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                MyApplicationTheme {
                    MainScreen(
                        viewModel = appViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // N16: splash поверх контента (~750ms), при deep-link — skip
                com.politex.ui.components.IntroSplashOverlay(skip = deepLink)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mainViewModel?.let { handleIntent(intent, it) }
    }

    override fun onResume() {
        super.onResume()
        // O1: updateAllWidgets отсюда убран — на каждый резюм (в т.ч. возврат из фона/диалогов)
        // это была пересборка 4 провайдеров × все инстансы × runBlocking в Room.
        // Виджеты обновляются минутным тиком (WidgetAlarm) и BellTimerService.
    }

    private fun handleIntent(intent: Intent?, viewModel: AppViewModel) {
        // Б7: тап по кнопке кабинета в виджете — сразу на карту с этим кабинетом
        // N8: EXTRA_SHOW_ROUTE (виджет NowNext) — открыть карту С маршрутом от главного входа
        val room = intent?.getStringExtra("EXTRA_ROOM")
        if (!room.isNullOrBlank()) {
            viewModel.openMap(
                room,
                fromRoom = null,
                showRoute = intent.getBooleanExtra("EXTRA_SHOW_ROUTE", false)
            )
            return
        }
        val scrollTarget = intent?.getStringExtra("EXTRA_SCROLL_TARGET")
        when (intent?.getStringExtra("EXTRA_OPEN_TAB")) {
            "SCHEDULE" -> viewModel.selectTab(AppTab.SCHEDULE)
            "BELLS" -> viewModel.selectTab(AppTab.BELLS)
            // H1: тап по уведомлению-напоминанию открывает вкладку заданий
            "TASKS" -> viewModel.selectTab(AppTab.TASKS)
            else -> return
        }
        // Виджет просит не только открыть вкладку, но и проскроллить к «сейчас»
        if (scrollTarget != null) {
            viewModel.requestScrollTarget(scrollTarget)
        }
    }
}

/**
 * N16: splash НЕ проигрывается, если приложение открыто из виджета/уведомления —
 * любой из extras означает «пользователь тапнул туда-то, заставка только мешает».
 * Чистая функция (без Android-зависимостей) — удобно покрыть юнит-тестом.
 */
fun shouldSkipSplash(hasOpenTab: Boolean, hasRoom: Boolean, hasScrollTarget: Boolean): Boolean =
    hasOpenTab || hasRoom || hasScrollTarget
