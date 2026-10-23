package com.politex.promo

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.politex.BuildConfig
import com.politex.ui.components.AppDivider
import com.politex.ui.components.MainTopBar
import com.politex.ui.screens.CollegeScreen
import com.politex.ui.teacher.TeacherMoreScreen
import com.politex.ui.teacher.TeacherProfile
import com.politex.ui.teacher.TeacherTheme
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorNavSelected
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.MyApplicationTheme
import com.politex.ui.viewmodel.AppTab
import com.politex.ui.viewmodel.tabsForMode
import com.politex.update.UpdateAsset
import com.politex.update.UpdateInfo
import com.politex.update.UpdateInstallController
import com.politex.util.GroupParser
import com.politex.util.TeacherModeStore
import com.politex.util.UpdateChannel
import com.politex.util.UpdateChannelStore
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxxhdpi")
class UpdateShotsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val group = GroupParser.parse("41О")!!

    private val profile = TeacherProfile(name = "Лебедева Ольга Дмитриевна", surnameKey = "лебедева")

    private val foundInfo =
        UpdateInfo(
            tagName = "v2.0.1",
            versionName = "2.0.1",
            versionCode = BuildConfig.VERSION_CODE + 1000,
            notes = "",
            asset = UpdateAsset("a.apk", "https://example.com/a.apk", 100L),
        )

    private val outDir: File =
        run {
            val cwd = File(System.getProperty("user.dir"))
            val root = if (cwd.name == "app") cwd.parentFile else cwd
            File(root, "promo/shots").apply { mkdirs() }
        }

    @Before
    fun setUp() {
        UpdateChannelStore.set(context, UpdateChannel.STABLE)
        TeacherModeStore.clear(context)
        resetController()
        UpdateInstallController.rememberFound(context, foundInfo)
    }

    @After
    fun tearDown() = resetController()

    private fun resetController() {
        UpdateInstallController.downloadOverride = null
        UpdateInstallController.validateOverride = null
        UpdateInstallController.installLaunchOverride = null
        UpdateInstallController.cancelAndReset(context)
    }

    @Test
    fun shotUpdateOtherMenu() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MyApplicationTheme {
                Scaffold(
                    topBar = { MainTopBar() },
                    bottomBar = { PromoNavBar(selected = 3) },
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        CollegeScreen(groupInfo = group)
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
        runCatching { waitForText("ПОЛИТЕХНИЧЕСКИЙ", 10_000) }
        waitForText("ДОСТУПНО ОБНОВЛЕНИЕ")
        scrollToUpdateCard()
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(File(outDir, "shot_update_other_menu.png").absolutePath)
    }

    @Test
    fun shotUpdateTeacherMore() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            TeacherTheme {
                TeacherMoreScreen(profile = profile)
            }
        }
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
        waitForText("ДОСТУПНО ОБНОВЛЕНИЕ")
        scrollToUpdateCard()
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(File(outDir, "shot_update_teacher_more.png").absolutePath)
    }

    private fun scrollToUpdateCard() {
        composeRule.onNodeWithTag("more_update_btn").performScrollTo()
        composeRule.waitForIdle()
    }

    private fun waitForText(
        text: String,
        timeoutMs: Long = 20_000,
    ) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(50)
            composeRule.mainClock.advanceTimeByFrame()
        }
        throw AssertionError("Текст «$text» не появился за ${timeoutMs}мс")
    }

    @Composable
    private fun PromoNavBar(selected: Int) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AppDivider()
            NavigationBar(
                containerColor = ColorBgMain,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth().height(72.dp),
            ) {
                val tabs =
                    tabsForMode(false).map { tab ->
                        when (tab) {
                            AppTab.SCHEDULE -> Triple("Расписание", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth)
                            AppTab.TASKS -> Triple("Уроки", Icons.Filled.MenuBook, Icons.Outlined.MenuBook)
                            AppTab.BELLS -> Triple("Звонки", Icons.Filled.AccessTime, Icons.Outlined.AccessTime)
                            AppTab.COLLEGE -> Triple("Другое", Icons.Filled.Apps, Icons.Outlined.Apps)
                        }
                    }
                tabs.forEachIndexed { i, (title, filledIcon, outlinedIcon) ->
                    val isSelected = selected == i
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) filledIcon else outlinedIcon,
                                contentDescription = title,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        label = {
                            Text(
                                text = title,
                                softWrap = false,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style =
                                TextStyle(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.sp,
                                ),
                            )
                        },
                        colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = ColorNavSelected,
                            selectedTextColor = ColorNavSelected,
                            indicatorColor = ColorSurfaceHighlight,
                            unselectedIconColor = ColorTextMuted,
                            unselectedTextColor = ColorTextMuted,
                        ),
                    )
                }
            }
        }
    }
}
