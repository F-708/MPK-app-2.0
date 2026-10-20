package com.politex

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.politex.ui.screens.GroupSchedulePicker
import com.politex.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class K4PickerLettersScrollTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val manyLettersCatalog =
        listOf("11А", "11Б", "11В", "11Г", "11Д", "11Е", "11Ж", "11О")
    private val fewLettersCatalog = listOf("41О", "41Д")

    private fun setPicker(
        catalog: List<String>,
        course: Int,
    ) {
        composeRule.setContent {
            MyApplicationTheme {
                GroupSchedulePicker(
                    selectedCourse = course,
                    onCourseSelected = {},
                    catalogCodes = catalog,
                    onGroupSelected = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `много букв - справа виден индикатор что можно листать`() {
        setPicker(manyLettersCatalog, 1)
        composeRule.onNodeWithTag("group_picker_letters_row").assertExists()
        composeRule.onNodeWithTag("group_picker_letters_more_end").assertExists()
        composeRule.onNodeWithTag("group_picker_letters_more_start").assertDoesNotExist()
    }

    @Test
    fun `после прокрутки букв появляется индикатор слева`() {
        setPicker(manyLettersCatalog, 1)
        composeRule
            .onNodeWithTag("group_picker_letters_row")
            .performScrollToIndex(3)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("group_picker_letters_more_start").assertExists()
    }

    @Test
    fun `мало букв - индикаторов скролла нет`() {
        setPicker(fewLettersCatalog, 4)
        composeRule.onNodeWithTag("group_picker_letters_row").assertExists()
        composeRule.onNodeWithTag("group_picker_letters_more_end").assertDoesNotExist()
        composeRule.onNodeWithTag("group_picker_letters_more_start").assertDoesNotExist()
    }

    @Test
    fun `выбор номера и буквы не сломан - код собирается и валидируется`() {
        setPicker(manyLettersCatalog, 1)
        composeRule.onNodeWithTag("group_picker_number_1").performClick()
        composeRule.onNodeWithTag("group_picker_letter_А").performClick()
        composeRule.onNodeWithTag("group_picker_code").assertTextContains("11А")
        composeRule.onNodeWithTag("group_picker_valid_icon").assertExists()
    }
}
