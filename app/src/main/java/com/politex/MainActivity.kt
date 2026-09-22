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
import kotlinx.coroutines.delay

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

            // Конфетти «уроки закончились»: реакция на событие + проверка раз в 30 сек
            val confettiTick by ConfettiController.events.collectAsState()
            var confettiActive by remember { mutableStateOf(false) }
            var confettiTrigger by remember { mutableLongStateOf(0L) }

            LaunchedEffect(confettiTick) {
                // Показываем только свежее событие (в течение 30 сек): не перезапускаем
                // конфетти при пересоздании экрана или возврате в приложение спустя время.
                if (confettiTick > 0L &&
                    System.currentTimeMillis() - confettiTick < 30_000L
                ) {
                    confettiTrigger = confettiTick
                    confettiActive = true
                }
            }

            LaunchedEffect(Unit) {
                val appContext = applicationContext
                ConfettiController.maybeCelebrate(appContext)
                while (true) {
                    delay(30_000)
                    ConfettiController.maybeCelebrate(appContext)
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
        ConfettiController.maybeCelebrate(this)
    }

    private fun handleIntent(intent: Intent?, viewModel: AppViewModel) {
        when (intent?.getStringExtra("EXTRA_OPEN_TAB")) {
            "SCHEDULE" -> viewModel.selectTab(AppTab.SCHEDULE)
            "BELLS" -> viewModel.selectTab(AppTab.BELLS)
        }
    }
}
