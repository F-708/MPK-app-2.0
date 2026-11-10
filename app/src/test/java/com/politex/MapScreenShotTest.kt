package com.politex

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.politex.ui.screens.CollegeMapScreen
import com.politex.ui.theme.MyApplicationTheme
import com.politex.ui.theme.applyDarkMode
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Скриншоты карты 360×780: этажи, маршрут, светлая и тёмная тема. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class MapScreenShotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun resetTheme() {
        applyDarkMode(false)
    }

    private fun shoot(
        name: String,
        initialRoom: String?,
        fromRoom: String?,
        floor: String? = null,
        dark: Boolean = false,
    ) {
        applyDarkMode(dark)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MyApplicationTheme {
                CollegeMapScreen(initialRoom = initialRoom, fromRoom = fromRoom, onBack = {})
            }
        }
        composeRule.mainClock.advanceTimeBy(600)
        if (floor != null) {
            composeRule.onNodeWithText(floor).performClick()
            composeRule.mainClock.advanceTimeBy(600)
        }
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test
    fun `карта из меню — без маршрута`() {
        shoot("map-menu", null, null)
    }

    @Test
    fun `кампус вокруг плана`() {
        shoot("map-campus", null, null)
    }

    @Test
    fun `3D вид кампуса`() {
        applyDarkMode(false)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MyApplicationTheme {
                CollegeMapScreen(initialRoom = null, fromRoom = null, onBack = {})
            }
        }
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("map_tilt_toggle").performClick()
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.onNodeWithTag("map_fit_world").performClick()
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/screenshots/map-campus-3d.png")
    }

    @Test
    fun `весь кампус по кнопке`() {
        applyDarkMode(false)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MyApplicationTheme {
                CollegeMapScreen(initialRoom = null, fromRoom = null, onBack = {})
            }
        }
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("map_fit_world").performClick()
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/screenshots/map-campus-fit.png")
    }

    @Test
    fun `карта из расписания — с маршрутом`() {
        shoot("map-route", "128", "101")
    }

    @Test
    fun `карта из расписания без маршрута`() {
        shoot("map-room-only", "128", null)
    }

    @Test
    fun `второй этаж`() {
        shoot("map-floor2", null, null, floor = "2 этаж")
    }

    @Test
    fun `третий и четвёртый этажи`() {
        shoot("map-floor34", null, null, floor = "3 и 4 этажи")
    }

    @Test
    fun `второй этаж с маршрутом`() {
        shoot("map-floor2-route", "215", "233", floor = "2 этаж")
    }

    @Test
    fun `тёмная тема — обзор`() {
        shoot("map-dark", null, null, dark = true)
    }

    @Test
    fun `тёмная тема — кабинет`() {
        shoot("map-dark-room", "128", null, dark = true)
    }
}