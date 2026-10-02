package com.politex

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.politex.ui.screens.MainScreen
import com.politex.ui.screens.buildMapRoute
import com.politex.ui.screens.buildScrollRoute
import com.politex.ui.screens.navigateToTab
import com.politex.ui.theme.MyApplicationTheme
import com.politex.ui.viewmodel.ARG_SCROLL
import com.politex.ui.viewmodel.AppTab
import com.politex.ui.viewmodel.AppViewModel
import com.politex.util.NotificationHelper
import com.politex.worker.MpkWorkManagerHelper

class MainActivity : ComponentActivity() {

    // E4: навигация — NavController (создаётся в setContent); интенты виджетов/уведомлений
    // вместо pending*-полей VM (ARC-01/ARC-06) вызывают navigate() с аргументами.
    private var navController: NavHostController? = null

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
            val navController = rememberNavController()
            this@MainActivity.navController = navController

            // Холодный старт: интент обрабатывается ПОСЛЕ первой композиции (NavHost жив)
            LaunchedEffect(Unit) {
                handleIntent(intent, navController)
            }

            androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                MyApplicationTheme {
                    MainScreen(
                        viewModel = appViewModel,
                        navController = navController,
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
        // Тёплый тап виджета → navigate (CLEAR_TOP|SINGLE_TOP активити не пересоздаёт)
        navController?.let { handleIntent(intent, it) }
    }

    override fun onResume() {
        super.onResume()
        // O1: updateAllWidgets отсюда убран — на каждый резюм (вкл. возврат из фона/диалогов)
        // это была пересборка 4 провайдеров × все инстансы × runBlocking в Room.
        // Виджеты обновляются минутным тиком (WidgetAlarm) и BellTimerService.
    }

    /**
     * E4: интенты виджетов/уведомлений → nav-аргументы вместо pending*-полей VM.
     * Б7: тап по кнопке кабинета в виджете — сразу на карту с этим кабинетом.
     * N8: EXTRA_SHOW_ROUTE (виджет NowNext) — открыть карту С маршрутом от главного входа.
     */
    private fun handleIntent(intent: Intent?, navController: NavHostController) {
        val room = intent?.getStringExtra("EXTRA_ROOM")
        if (!room.isNullOrBlank()) {
            navController.navigate(
                buildMapRoute(room, from = null, showRoute = intent.getBooleanExtra("EXTRA_SHOW_ROUTE", false))
            )
            return
        }
        val scrollTarget = intent?.getStringExtra("EXTRA_SCROLL_TARGET")
        val tab = when (intent?.getStringExtra("EXTRA_OPEN_TAB")) {
            "SCHEDULE" -> AppTab.SCHEDULE
            "BELLS" -> AppTab.BELLS
            // H1: тап по уведомлению-напоминанию открывает вкладку заданий
            "TASKS" -> AppTab.TASKS
            else -> null
        } ?: return
        navController.navigateToTab(buildScrollRoute(tab.route, scrollTarget))
        // Виджет просит не только открыть вкладку, но и проскроллить к «сейчас».
        // Пишем в SavedStateHandle записи: одноразовое чтение (см. rememberScrollRequest),
        // повторный тёплый тап эмитит снова даже при launchSingleTop (аргументы записи
        // при переиспользовании не обновляются — handle обновляется всегда).
        if (!scrollTarget.isNullOrBlank()) {
            navController.currentBackStackEntry?.savedStateHandle?.set(ARG_SCROLL, scrollTarget)
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
