package com.example

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
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.AppViewModel
import com.example.util.NotificationHelper
import com.example.widget.WidgetUpdateHelper
import com.example.worker.MpkWorkManagerHelper

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Создаем канал уведомлений и регистрируем периодический WorkManager
        NotificationHelper.createNotificationChannel(this)
        MpkWorkManagerHelper.setupPeriodicScheduleCheck(this)

        setContent {
            val appViewModel: AppViewModel = viewModel()
            val uiState by appViewModel.uiState.collectAsState()

            // Обработка клика по виджету или пуш-уведомлению
            LaunchedEffect(intent) {
                handleIntent(intent, appViewModel)
            }

            MyApplicationTheme(
                darkTheme = uiState.isDarkTheme,
                dynamicColor = false // Сохранение фирменных цветов МГПК
            ) {
                MainScreen(
                    viewModel = appViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // При возвращении в приложение обновляем виджеты
        WidgetUpdateHelper.updateAllWidgets(this)
    }

    private fun handleIntent(intent: Intent?, viewModel: AppViewModel) {
        val tab = intent?.getStringExtra("EXTRA_OPEN_TAB")
        if (tab == "SCHEDULE") {
            viewModel.selectTab(AppTab.SCHEDULE)
        }
    }
}

