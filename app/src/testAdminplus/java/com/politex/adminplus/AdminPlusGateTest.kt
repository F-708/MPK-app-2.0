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
import androidx.test.core.app.ApplicationProvider
import com.politex.owner.AccessUnlockStore
import com.politex.owner.OwnerNav
import com.politex.ui.theme.MyApplicationTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Заказчик (2026-11-12): adminplus — личное приложение владельца, код доступа
 * не требуется. Проверяем, что инструменты открыты сразу, без гейта.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp")
class AdminPlusGateTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        AccessUnlockStore.reset(context)
        AdminPlusNav.reset()
        OwnerNav.showAccessCode.value = false
    }

    @After
    fun tearDown() {
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

    @Test
    fun `adminplus открывается без кода доступа`() {
        renderRoot()

        composeRule.onAllNodesWithTag("access_code_input").assertCountEquals(0)
        composeRule.onNodeWithTag("main_content").assertIsDisplayed()
        composeRule.onNodeWithTag("adminplus_data_tools_menu").assertIsDisplayed()
        composeRule.onNodeWithTag("adminplus_pat_menu").assertIsDisplayed()
    }
}
