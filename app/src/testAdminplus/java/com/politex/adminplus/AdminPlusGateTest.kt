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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.politex.owner.AccessClock
import com.politex.owner.AccessCode
import com.politex.owner.AccessUnlockStore
import com.politex.owner.OwnerNav
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

/**
 * P1-fix: adminplus — самый привилегированный флейвор (редактор данных преподавателей
 * и PAT публикации), поэтому он обязан требовать код доступа так же, как admin.
 *
 * Проверяем: пока код не введён (гейт не пройден) adminplus-инструменты не отрисованы;
 * неверный код не открывает их; верный — открывает; флаг разблокировки персистентен.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp")
class AdminPlusGateTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val epoch = 1_760_000_015L

    @Before
    fun setUp() {
        AccessClock.overrideSeconds = epoch
        AccessUnlockStore.reset(context)
        AdminPlusNav.reset()
        OwnerNav.showAccessCode.value = false
    }

    @After
    fun tearDown() {
        AccessClock.overrideSeconds = null
        AccessUnlockStore.reset(context)
        AdminPlusNav.reset()
        OwnerNav.showAccessCode.value = false
    }

    private fun renderRoot() {
        composeRule.setContent {
            MyApplicationTheme {
                AdminPlusRoot {
                    Column(modifier = Modifier.testTag("main_content")) {
                        Text(text = "ОСНОВНОЙ ЭКРАН")
                        AdminPlusMenuExtras()
                    }
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
    fun `без кода adminplus-инструменты закрыты`() {
        assertTrue("в adminplus гейт обязателен всегда", AdminPlusAccess.GATE_REQUIRED)

        renderRoot()

        composeRule.onNodeWithTag("access_code_input").assertIsDisplayed()
        composeRule.onAllNodesWithTag("main_content").assertCountEquals(0)
        composeRule.onAllNodesWithTag("adminplus_data_tools_menu").assertCountEquals(0)
        composeRule.onAllNodesWithTag("adminplus_pat_menu").assertCountEquals(0)
        composeRule.onAllNodesWithTag("owner_access_code_menu").assertCountEquals(0)
        assertFalse(AccessUnlockStore.isUnlocked(context))
        assertFalse(AdminPlusNav.showDataTools.value)
    }

    @Test
    fun `неверный код не открывает инструменты`() {
        renderRoot()

        composeRule.onNodeWithTag("access_code_input").performTextInput(wrongCode())
        composeRule.onNodeWithTag("access_code_submit").performClick()

        composeRule.onAllNodesWithTag("main_content").assertCountEquals(0)
        composeRule.onAllNodesWithTag("adminplus_data_tools_menu").assertCountEquals(0)
        composeRule.onAllNodesWithTag("adminplus_pat_menu").assertCountEquals(0)
        assertFalse(AccessUnlockStore.isUnlocked(context))
    }

    @Test
    fun `верный код открывает основной UI и меню владельца`() {
        renderRoot()

        composeRule.onNodeWithTag("access_code_input").performTextInput(AccessCode.generateCode(epoch))
        composeRule.onNodeWithTag("access_code_submit").performClick()

        composeRule.onNodeWithTag("main_content").assertIsDisplayed()
        composeRule.onNodeWithTag("adminplus_data_tools_menu").assertIsDisplayed()
        composeRule.onNodeWithTag("adminplus_pat_menu").assertIsDisplayed()
        assertTrue(AccessUnlockStore.isUnlocked(context))
    }

    @Test
    fun `разблокировка переживает перезапуск`() {
        AccessUnlockStore.unlock(context)

        renderRoot()

        composeRule.onNodeWithTag("main_content").assertIsDisplayed()
        composeRule.onAllNodesWithTag("access_code_input").assertCountEquals(0)
    }
}