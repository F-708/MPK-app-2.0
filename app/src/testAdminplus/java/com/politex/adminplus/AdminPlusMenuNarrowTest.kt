package com.politex.adminplus

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.theme.MyApplicationTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w320dp-h640dp")
class AdminPlusMenuNarrowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `все карточки владельца на узком экране - заголовки и бейджи в одну строку`() {
        composeRule.setContent {
            MyApplicationTheme { AdminPlusMenuExtras() }
        }

        // Гейт в adminplus отключён (2026-11-12): карточки кода доступа нет.
        composeRule.onAllNodesWithText("КОД ДОСТУПА").assertCountEquals(0)
        composeRule.onNodeWithText("ИНСТРУМЕНТЫ ДАННЫХ").assertIsDisplayed()
        composeRule.onNodeWithText("ТОКЕН ПУБЛИКАЦИИ").assertIsDisplayed()
        composeRule.onNodeWithText("ОФОРМЛЕНИЕ И ПОГОДА").assertIsDisplayed()

        val badges = composeRule.onAllNodesWithText("OWNER", useUnmergedTree = true)
        badges.assertCountEquals(3)
        val scaledDensity = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.scaledDensity
        for (index in 0..2) {
            val bounds = badges[index].fetchSemanticsNode().boundsInRoot
            assertTrue(
                "бейдж $index переносится на следующую строку: height=${bounds.height}px",
                bounds.height < 18 * scaledDensity,
            )
        }
    }
}
