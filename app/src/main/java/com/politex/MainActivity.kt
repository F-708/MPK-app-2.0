package com.politex

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.politex.ui.components.ConfettiOverlay
import com.politex.ui.screens.MainScreen
import com.politex.ui.theme.MyApplicationTheme
import com.politex.ui.viewmodel.AppTab
import com.politex.ui.viewmodel.AppViewModel
import com.politex.util.ConfettiController
import com.politex.util.NotificationHelper
import com.politex.widget.WidgetUpdateHelper
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

        setContent {
            val appViewModel: AppViewModel = viewModel()
            mainViewModel = appViewModel
            val uiState by appViewModel.uiState.collectAsState()

            LaunchedEffect(intent) {
                handleIntent(intent, appViewModel)
            }

            // Конфетти: только реакция на свежее событие из ConfettiController.events.
            // Сам триггер живёт в минутных тиках (WidgetAlarmReceiver / BellTimerService):
            // здесь нет ни запуска по открытию/резюму, ни поллинга — событие, случившееся
            // в фоне (тик виджета при закрытом приложении), не проигрывается заново:
            // старше CONFETTI_MAX_EVENT_AGE_MS — игнорируем.
            val confettiTick by ConfettiController.events.collectAsState()
            var confettiActive by remember { mutableStateOf(false) }
            var confettiTrigger by remember { mutableLongStateOf(0L) }

            LaunchedEffect(confettiTick) {
                if (confettiTick > 0L &&
                    System.currentTimeMillis() - confettiTick < ConfettiController.MAX_EVENT_AGE_MS
                ) {
                    confettiTrigger = confettiTick
                    confettiActive = true
                }
            }

            MyApplicationTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        viewModel = appViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (confettiActive) {
                        ConfettiOverlay(
                            trigger = confettiTrigger,
                            onDone = { confettiActive = false }
                        )
                    }
                }
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
        WidgetUpdateHelper.updateAllWidgets(this)
    }

    private fun handleIntent(intent: Intent?, viewModel: AppViewModel) {
        val scrollTarget = intent?.getStringExtra("EXTRA_SCROLL_TARGET")
        when (intent?.getStringExtra("EXTRA_OPEN_TAB")) {
            "SCHEDULE" -> viewModel.selectTab(AppTab.SCHEDULE)
            "BELLS" -> viewModel.selectTab(AppTab.BELLS)
            else -> return
        }
        // Виджет просит не только открыть вкладку, но и проскроллить к «сейчас»
        if (scrollTarget != null) {
            viewModel.requestScrollTarget(scrollTarget)
        }
    }
}
