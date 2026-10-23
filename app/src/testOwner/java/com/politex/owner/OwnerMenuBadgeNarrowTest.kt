package com.politex.owner

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
class OwnerMenuBadgeNarrowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `бейдж OWNER на узком экране в одну строку`() {
        composeRule.setContent {
            MyApplicationTheme { OwnerMenuExtras() }
        }

        composeRule.onNodeWithText("КОД ДОСТУПА").assertIsDisplayed()
        val badge = composeRule.onNodeWithText("OWNER", useUnmergedTree = true)
        badge.assertIsDisplayed()
        val bounds = badge.fetchSemanticsNode().boundsInRoot
        val scaledDensity = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.scaledDensity
        assertTrue(
            "бейдж переносится на следующую строку: height=${bounds.height}px",
            bounds.height < 18 * scaledDensity,
        )
    }
}
