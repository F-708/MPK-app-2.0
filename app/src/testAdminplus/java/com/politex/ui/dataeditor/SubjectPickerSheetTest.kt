package com.politex.ui.dataeditor

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.politex.ui.theme.MyApplicationTheme
import com.politex.util.MpkCurriculum
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class SubjectPickerSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `лист показывает поиск список и элементы каталога`() {
        assertTrue(MpkCurriculum.ALL_SUBJECTS.isNotEmpty())
        composeRule.setContent {
            MyApplicationTheme {
                SubjectPickerSheet(initial = "", onDismiss = {}, onConfirm = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("subject_picker_sheet").fetchSemanticsNode()
        composeRule.onNodeWithTag("subject_picker_list").fetchSemanticsNode()
        composeRule.onNodeWithTag("subject_picker_search").fetchSemanticsNode()
        val first = SubjectPickerLogic.catalog(emptyList()).first()
        composeRule.onAllNodesWithTag("subject_picker_item_$first").assertCountEquals(1)
    }
}
