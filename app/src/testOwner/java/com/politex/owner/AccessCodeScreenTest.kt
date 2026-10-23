package com.politex.owner

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.theme.MyApplicationTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp")
class AccessCodeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val epoch = 1_760_000_015L

    @Before
    fun setUp() {
        AccessClock.overrideSeconds = epoch
    }

    @After
    fun tearDown() {
        AccessClock.overrideSeconds = null
    }

    private fun render() {
        composeRule.setContent {
            MyApplicationTheme {
                AccessCodeScreen(onBack = {})
            }
        }
    }

    @Test
    fun `цифры кода и подписи видны`() {
        render()

        composeRule.onNodeWithTag("access_code_digits").assertIsDisplayed()
        composeRule.onAllNodesWithText(AccessCode.generateCode(epoch)).assertCountEquals(1)
        composeRule.onNodeWithText("Обновляется каждые 30 секунд").assertIsDisplayed()
        composeRule.onNodeWithText("Код действителен ~30 секунд после смены").assertIsDisplayed()
        composeRule.onNodeWithText("Основан на времени этого устройства").assertIsDisplayed()
        composeRule.onNodeWithTag("access_code_ring").assertIsDisplayed()
    }

    @Test
    fun `при сдвиге clock-override код обновляется`() {
        render()

        val first = AccessCode.generateCode(epoch)
        val second = AccessCode.generateCode(epoch + 30)

        AccessClock.overrideSeconds = epoch + 30
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(second).fetchSemanticsNodes().size == 1 &&
                composeRule.onAllNodesWithText(first).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun `остаток секунд следует за clock-override`() {
        render()

        composeRule.onNodeWithText("25 с").assertIsDisplayed()

        AccessClock.overrideSeconds = epoch + 20
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("5 с").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `копирование кладёт код в буфер и показывает статус`() {
        render()

        composeRule.onNodeWithTag("access_code_copy_btn").performClick()

        composeRule.onNodeWithText("Скопировано").assertIsDisplayed()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(AccessCode.generateCode(epoch), clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun `кнопка назад вызывается`() {
        var backClicks = 0
        composeRule.setContent {
            MyApplicationTheme {
                AccessCodeScreen(onBack = { backClicks++ })
            }
        }

        composeRule.onNodeWithContentDescription("Назад").performClick()

        assertEquals(1, backClicks)
    }
}
