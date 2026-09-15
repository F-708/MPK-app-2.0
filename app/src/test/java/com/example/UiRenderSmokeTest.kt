package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MpkDatabase
import com.example.data.repository.ScheduleRepository
import com.example.data.repository.TaskRepository
import com.example.ui.screens.BellsScreen
import com.example.ui.screens.ScheduleScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextStylePageTitle
import com.example.util.GroupParser
import androidx.compose.material3.Text
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Дымовой рендер-тест: реально отрисовывает все экраны в Robolectric.
 * Ловит краши композиции (например, @Composable-геттеры вне композиции),
 * которые не видят логические юнит-тесты.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp")
class UiRenderSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val group = GroupParser.parse("41О")!!

    @Test
    fun `светлая тема и текстовые стили рендерятся`() {
        composeRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                Text(text = "Светлая", style = TextStylePageTitle)
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `тёмная тема и текстовые стили рендерятся`() {
        composeRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                Text(text = "Тёмная", style = TextStylePageTitle)
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `экран расписания рендерится`() {
        val repo = ScheduleRepository(MpkDatabase.getInstance(context).lessonDao())
        composeRule.setContent {
            MyApplicationTheme {
                ScheduleScreen(groupInfo = group, scheduleRepository = repo, onSyncRequest = {})
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `экран звонков рендерится`() {
        composeRule.setContent {
            MyApplicationTheme {
                BellsScreen()
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `экран заданий рендерится`() {
        val repo = TaskRepository(MpkDatabase.getInstance(context).taskDao())
        composeRule.setContent {
            MyApplicationTheme {
                TasksScreen(groupInfo = group, taskRepository = repo)
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `экран настроек рендерится`() {
        composeRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    groupInfo = group,
                    isDarkTheme = false,
                    onThemeChanged = {},
                    onGroupChanged = {},
                    onRunConnectionTest = {}
                )
            }
        }
        composeRule.waitForIdle()
    }
}
