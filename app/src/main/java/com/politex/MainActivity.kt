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

    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        com.politex.ui.theme
            .applyAppTheme(
                com.politex.util.AppThemeStore
                    .load(this),
            )

        NotificationHelper.createNotificationChannel(this)
        com.politex.util.BellCountdownNotifier
            .createChannel(this)

        if (com.politex.util.BellCountdownNotifier
                .isEnabled(this)
        ) {
            com.politex.util.BellTimerService
                .start(this)
        }
        if (BuildConfig.FLAVOR == "teacher") {
            MpkWorkManagerHelper.setupTeacherSync(this)
        } else {
            MpkWorkManagerHelper.setupPeriodicScheduleCheck(this)
            MpkWorkManagerHelper.setupBellServiceWatchdog(this)
            MpkWorkManagerHelper.setupPeriodicReminderCheck(this)
        }

        setContent {
            val appViewModel: AppViewModel = viewModel()
            val navController = rememberNavController()
            this@MainActivity.navController = navController

            LaunchedEffect(Unit) {
                handleIntent(intent, navController)
            }

            // Единственная точка ветвления flavor (долг чести №6: ноль FLAVOR в остальном коде)
            if (BuildConfig.FLAVOR == "teacher") {
                com.politex.ui.teacher.TeacherRoot(modifier = Modifier.fillMaxSize())
            } else {
                MyApplicationTheme {
                    MainScreen(
                        viewModel = appViewModel,
                        navController = navController,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        navController?.let { handleIntent(intent, it) }
    }

    override fun onResume() {
        super.onResume()

    }

    private fun handleIntent(
        intent: Intent?,
        navController: NavHostController,
    ) {
        val room = intent?.getStringExtra("EXTRA_ROOM")
        if (!room.isNullOrBlank()) {
            navController.navigate(
                buildMapRoute(
                    room,
                    from = intent.getStringExtra("EXTRA_FROM_ROOM"),
                    showRoute = intent.getBooleanExtra("EXTRA_SHOW_ROUTE", false),
                ),
            )
            return
        }
        val scrollTarget = intent?.getStringExtra("EXTRA_SCROLL_TARGET")
        val tab =
            when (intent?.getStringExtra("EXTRA_OPEN_TAB")) {
                "SCHEDULE" -> AppTab.SCHEDULE
                "BELLS" -> AppTab.BELLS

                "TASKS" -> AppTab.TASKS
                else -> null
            } ?: return
        navController.navigateToTab(buildScrollRoute(tab.route, scrollTarget))

        if (!scrollTarget.isNullOrBlank()) {
            navController.currentBackStackEntry?.savedStateHandle?.set(ARG_SCROLL, scrollTarget)
        }
    }
}
