package com.politex

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.politex.ui.components.FirstRunGroupDialog
import com.politex.ui.components.GroupSelectionDialog
import com.politex.ui.components.groupDialogVerticalInsets
import com.politex.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h480dp")
class Ll1GroupDialogUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val group41O = "41О"

    private fun pick41O() {
        composeRule.onNodeWithTag("group_schedule_course_4").performClick()
        composeRule.onNodeWithTag("group_picker_number_1").performScrollTo().performClick()
        composeRule.onNodeWithTag("group_picker_letter_${group41O[2]}").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun setFirstRun() {
        composeRule.setContent {
            MyApplicationTheme {
                FirstRunGroupDialog(
                    onComplete = { _, _ -> },
                    onTeacherSelected = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `онбординг - 41О и кнопка ДАЛЕЕ видима и активна на низком экране`() {
        setFirstRun()
        composeRule.onNodeWithTag("first_run_dialog").assertExists()
        pick41O()
        composeRule.onNodeWithTag("group_picker_code").assertTextContains(group41O)
        composeRule
            .onNodeWithTag("group_picker_open_button")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertTextContains("ДАЛЕЕ")
    }

    @Test
    fun `онбординг - кнопка вне скролла и остаётся видимой после прокрутки букв`() {
        setFirstRun()
        pick41O()
        composeRule.onNodeWithTag("group_picker_preview").performScrollTo()
        composeRule
            .onNodeWithTag("group_picker_open_button")
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun `смена группы - кнопка ВЫБРАТЬ видима и активна на низком экране`() {
        composeRule.setContent {
            MyApplicationTheme {
                GroupSelectionDialog(
                    currentGroupName = group41O,
                    onDismissRequest = {},
                    onGroupSelected = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("group_selection_dialog").assertExists()
        pick41O()
        composeRule.onNodeWithTag("group_picker_code").assertTextContains(group41O)
        composeRule
            .onNodeWithTag("group_picker_open_button")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertTextContains("ВЫБРАТЬ")
    }
}

class Ll2GroupDialogInsetsTest {

    @Test
    fun `без клавиатуры просветы сверху и снизу равны`() {
        val insets = groupDialogVerticalInsets(systemBarTop = 24.dp, systemBarBottom = 48.dp, imeBottom = 0.dp)
        assertEquals(60.dp, insets.top)
        assertEquals(60.dp, insets.bottom)
    }

    @Test
    fun `клавиатура поднимает низ и не сжимает верх`() {
        val insets = groupDialogVerticalInsets(systemBarTop = 24.dp, systemBarBottom = 48.dp, imeBottom = 300.dp)
        assertEquals(36.dp, insets.top)
        assertEquals(312.dp, insets.bottom)
    }

    @Test
    fun `ime меньше навигационной панели не ломает симметрию`() {
        val insets = groupDialogVerticalInsets(systemBarTop = 24.dp, systemBarBottom = 48.dp, imeBottom = 48.dp)
        assertEquals(60.dp, insets.top)
        assertEquals(60.dp, insets.bottom)
    }

    @Test
    fun `api 24-29 с нулевыми insets даёт 12dp`() {
        val insets = groupDialogVerticalInsets(systemBarTop = 0.dp, systemBarBottom = 0.dp, imeBottom = 0.dp)
        assertEquals(12.dp, insets.top)
        assertEquals(12.dp, insets.bottom)
    }
}