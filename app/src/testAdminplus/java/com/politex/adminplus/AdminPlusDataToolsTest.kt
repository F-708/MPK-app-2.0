package com.politex.adminplus

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.politex.AppGraph
import com.politex.content.OwnerOverridesStore
import com.politex.owner.AccessUnlockStore
import com.politex.owner.OwnerNav
import com.politex.ui.theme.MyApplicationTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class AdminPlusDataToolsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        AdminPlusNav.reset()
        OwnerNav.showAccessCode.value = false
        // adminplus теперь закрыт кодом доступа (AdminPlusAccess.GATE_REQUIRED):
        // тесты навигации по инструментам данных работают «из разблокированного» состояния.
        AccessUnlockStore.unlock(context)
        OwnerOverridesStore.clearAll(context)
        AppGraph.teachersRepository.invalidateCache()
        com.politex.ui.util.TeachersCache.invalidate()
    }

    @After
    fun tearDown() {
        AdminPlusNav.reset()
        OwnerNav.showAccessCode.value = false
        AccessUnlockStore.reset(context)
        OwnerOverridesStore.clearAll(context)
        AppGraph.teachersRepository.invalidateCache()
        com.politex.ui.util.TeachersCache.invalidate()
    }

    private fun renderRootWithMenuProbe() {
        composeRule.setContent {
            MyApplicationTheme {
                AdminPlusRoot {
                    Column(modifier = Modifier.testTag("other_menu_probe")) {
                        Text(text = "ДРУГОЕ")
                        AdminPlusMenuExtras()
                    }
                }
            }
        }
    }

    @Test
    fun `пункт инструменты данных виден и открывает список правки`() {
        renderRootWithMenuProbe()

        // Гейт в adminplus отключён (2026-11-12): карточки кода доступа нет.
        composeRule.onAllNodesWithTag("owner_access_code_menu").assertCountEquals(0)
        composeRule.onNodeWithTag("adminplus_data_tools_menu").assertIsDisplayed()
        composeRule.onNodeWithText("ИНСТРУМЕНТЫ ДАННЫХ").assertIsDisplayed()

        composeRule.onNodeWithTag("adminplus_data_tools_menu").performClick()

        waitForTag("teacher_editor_list")
        composeRule.onAllNodesWithTag("other_menu_probe").assertCountEquals(0)
        assertTrue(AdminPlusNav.showDataTools.value)
    }

    @Test
    fun `системный назад ведёт редактор - список - другое`() {
        val teacher = AppGraph.teachersRepository.loadTeachers().first()
        renderRootWithMenuProbe()

        composeRule.onNodeWithTag("adminplus_data_tools_menu").performClick()
        waitForTag("teacher_editor_list")
        composeRule.onNodeWithTag("teacher_editor_search").performTextInput(teacher.name.substringBefore(' '))
        waitForTag("teacher_editor_row_${teacher.name}")
        composeRule.onNodeWithTag("teacher_editor_row_${teacher.name}").performClick()

        waitForTag("teacher_editor_screen")
        assertEquals(teacher.name, AdminPlusNav.editingTeacher.value)

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        waitForTag("teacher_editor_list")
        composeRule.onAllNodesWithTag("teacher_editor_screen").assertCountEquals(0)

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        pump()
        composeRule.onNodeWithTag("other_menu_probe").assertIsDisplayed()
        assertFalse(AdminPlusNav.showDataTools.value)
    }

    @Test
    fun `экранные кнопки назад повторяют цепочку`() {
        val teacher = AppGraph.teachersRepository.loadTeachers().first()
        renderRootWithMenuProbe()

        composeRule.onNodeWithTag("adminplus_data_tools_menu").performClick()
        waitForTag("teacher_editor_list")
        composeRule.onNodeWithTag("teacher_editor_search").performTextInput(teacher.name.substringBefore(' '))
        waitForTag("teacher_editor_row_${teacher.name}")
        composeRule.onNodeWithTag("teacher_editor_row_${teacher.name}").performClick()
        waitForTag("teacher_editor_screen")

        composeRule.onNodeWithContentDescription("Назад").performClick()
        waitForTag("teacher_editor_list")

        composeRule.onNodeWithContentDescription("Назад").performClick()
        pump()
        composeRule.onNodeWithTag("other_menu_probe").assertIsDisplayed()
        assertFalse(AdminPlusNav.showDataTools.value)
    }

    @Test
    fun `открытие списка напрямую и возврат кнопкой списка`() {
        renderRootWithMenuProbe()

        AdminPlusNav.openDataTools()
        waitForTag("teacher_editor_list")

        composeRule.onNodeWithContentDescription("Назад").performClick()
        pump()
        composeRule.onNodeWithTag("other_menu_probe").assertIsDisplayed()
        assertFalse(AdminPlusNav.showDataTools.value)
    }

    private fun waitForTag(
        tag: String,
        timeoutMs: Long = 20_000,
    ) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) return
            pump()
        }
        throw AssertionError("Узел «$tag» не появился за ${timeoutMs}мс")
    }

    private fun pump() {
        Thread.sleep(50)
        if (composeRule.mainClock.autoAdvance) composeRule.waitForIdle() else composeRule.mainClock.advanceTimeByFrame()
    }
}
