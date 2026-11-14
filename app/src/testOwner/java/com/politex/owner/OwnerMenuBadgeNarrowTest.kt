package com.politex.owner

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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

        // Карточки кода есть только там, где гейт включён (admin); в adminplus
        // гейт отключён (2026-11-12) и owner-меню пусто — тест неприменим.
        val badges = composeRule.onAllNodesWithText("OWNER", useUnmergedTree = true)
        org.junit.Assume.assumeTrue(
            "в этом flavor owner-меню пусто",
            badges.fetchSemanticsNodes().isNotEmpty(),
        )
        val badge = badges.onFirst()
        badge.assertIsDisplayed()
        val bounds = badge.fetchSemanticsNode().boundsInRoot
        val scaledDensity = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.scaledDensity
        assertTrue(
            "бейдж переносится на следующую строку: height=${bounds.height}px",
            bounds.height < 18 * scaledDensity,
        )
    }
}
