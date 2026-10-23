package com.politex.owner

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.politex.BuildConfig
import com.politex.ui.FlavorOverlay
import com.politex.ui.theme.MyApplicationTheme
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp")
class OwnerMenuExtrasFlavorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `регистрация меню владельца соответствует flavor`() {
        val extras = FlavorOverlay.collegeMenuExtras
        if (BuildConfig.FLAVOR == "adminplus") {
            assertNotNull("в adminplus пункт меню владельца регистрируется", extras)
            composeRule.setContent {
                MyApplicationTheme { extras!!() }
            }
            composeRule.onNodeWithText("КОД ДОСТУПА").assertIsDisplayed()
            composeRule.onNodeWithTag("owner_access_code_menu").assertIsDisplayed()
            composeRule.onNodeWithText("ИНСТРУМЕНТЫ ДАННЫХ").assertIsDisplayed()
            composeRule.onNodeWithTag("adminplus_data_tools_menu").assertIsDisplayed()
        } else {
            assertNull("в admin инструменты данных недоступны — extras не регистрируется", extras)
        }
    }
}
