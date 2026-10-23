package com.politex.owner

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
class AccessGateScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val epoch = 1_760_000_015L

    @Before
    fun setUp() {
        AccessClock.overrideSeconds = epoch
        AccessUnlockStore.reset(context)
    }

    @After
    fun tearDown() {
        AccessClock.overrideSeconds = null
        AccessUnlockStore.reset(context)
    }

    private fun renderGate() {
        composeRule.setContent {
            MyApplicationTheme {
                AccessGateWrapper(gateRequired = true) {
                    Text(
                        text = "ОСНОВНОЙ ЭКРАН",
                        modifier = Modifier.testTag("main_content"),
                    )
                }
            }
        }
    }

    private fun wrongCode(): String {
        val valid = AccessCode.generateCode(epoch)
        return ('0'..'9')
            .map { valid.take(5) + it }
            .first { !AccessCode.isCodeValid(it, epoch) }
    }

    @Test
    fun `не разблокирован - gate виден, основной UI скрыт`() {
        renderGate()

        composeRule.onNodeWithText("КОД ДОСТУПА").assertIsDisplayed()
        composeRule.onNodeWithText("Запросите код у владельца приложения").assertIsDisplayed()
        composeRule.onNodeWithTag("access_code_input").assertIsDisplayed()
        composeRule.onNodeWithTag("access_code_submit").assertIsDisplayed()
        composeRule.onAllNodesWithTag("main_content").assertCountEquals(0)
    }

    @Test
    fun `неверный код - ошибка, флаг не ставится`() {
        renderGate()

        composeRule.onNodeWithTag("access_code_input").performTextInput(wrongCode())
        composeRule.onNodeWithTag("access_code_submit").performClick()

        composeRule.onNodeWithText("Код неверен или устарел — запросите новый").assertIsDisplayed()
        composeRule.onAllNodesWithTag("main_content").assertCountEquals(0)
        assertFalse(AccessUnlockStore.isUnlocked(context))
    }

    @Test
    fun `верный код - основной UI и флаг разблокировки`() {
        renderGate()

        composeRule.onNodeWithTag("access_code_input").performTextInput(AccessCode.generateCode(epoch))
        composeRule.onNodeWithTag("access_code_submit").performClick()

        composeRule.onNodeWithTag("main_content").assertIsDisplayed()
        composeRule.onAllNodesWithTag("access_code_input").assertCountEquals(0)
        assertTrue(AccessUnlockStore.isUnlocked(context))
    }

    @Test
    fun `код соседнего окна тоже принимается`() {
        renderGate()

        composeRule.onNodeWithTag("access_code_input").performTextInput(AccessCode.generateCode(epoch - 30))
        composeRule.onNodeWithTag("access_code_submit").performClick()

        composeRule.onNodeWithTag("main_content").assertIsDisplayed()
        assertTrue(AccessUnlockStore.isUnlocked(context))
    }

    @Test
    fun `флаг персистентен - повторный вход без gate`() {
        AccessUnlockStore.unlock(context)
        assertTrue(AccessUnlockStore.isUnlocked(context))

        renderGate()

        composeRule.onNodeWithTag("main_content").assertIsDisplayed()
        composeRule.onAllNodesWithTag("access_code_input").assertCountEquals(0)
    }

    @Test
    fun `gate не требуется - основной UI сразу`() {
        composeRule.setContent {
            MyApplicationTheme {
                AccessGateWrapper(gateRequired = false) {
                    Text(
                        text = "ОСНОВНОЙ ЭКРАН",
                        modifier = Modifier.testTag("main_content"),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("main_content").assertIsDisplayed()
        composeRule.onAllNodesWithTag("access_code_input").assertCountEquals(0)
        assertFalse(AccessUnlockStore.isUnlocked(context))
    }

    @Test
    fun `поле принимает только цифры и не больше шести`() {
        renderGate()

        composeRule.onNodeWithTag("access_code_input").performTextInput("12ab34cd56ef78")

        composeRule.onNodeWithText("123456").assertExists()
    }
}
