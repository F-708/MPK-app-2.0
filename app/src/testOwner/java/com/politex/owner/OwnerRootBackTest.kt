package com.politex.owner

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.theme.MyApplicationTheme
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp")
class OwnerRootBackTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        AccessClock.overrideSeconds = 1_760_000_015L
        AccessUnlockStore.unlock(context)
        OwnerNav.showAccessCode.value = false
    }

    @After
    fun tearDown() {
        AccessClock.overrideSeconds = null
        AccessUnlockStore.reset(context)
        OwnerNav.showAccessCode.value = false
    }

    private fun renderRoot() {
        composeRule.setContent {
            MyApplicationTheme {
                OwnerRoot {
                    Text(
                        text = "ОСНОВНОЙ ЭКРАН",
                        modifier = Modifier.testTag("main_content"),
                    )
                }
            }
        }
    }

    @Test
    fun `системный назад возвращает с экрана кода в основной UI`() {
        OwnerNav.showAccessCode.value = true
        renderRoot()

        composeRule.onNodeWithTag("access_code_digits").assertIsDisplayed()
        composeRule.onAllNodesWithTag("main_content").assertCountEquals(0)

        composeRule.activity.onBackPressedDispatcher.onBackPressed()

        composeRule.onNodeWithTag("main_content").assertIsDisplayed()
        assertFalse(OwnerNav.showAccessCode.value)
    }

    @Test
    fun `кнопка назад на экране кода тоже возвращает в основной UI`() {
        OwnerNav.showAccessCode.value = true
        renderRoot()

        composeRule.onNodeWithTag("access_code_digits").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Назад").performClick()

        composeRule.onNodeWithTag("main_content").assertIsDisplayed()
        assertFalse(OwnerNav.showAccessCode.value)
        assertTrue(composeRule.onAllNodesWithTag("access_code_digits").fetchSemanticsNodes().isEmpty())
    }
}
