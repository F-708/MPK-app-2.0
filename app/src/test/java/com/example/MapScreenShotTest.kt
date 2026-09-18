package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.screens.CollegeMapScreen
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Снимает экран карты колледжа в PNG.
 *
 * Нужен, чтобы видеть вёрстку глазами: логические тесты не замечают, что
 * элемент уехал за край или налез на другой. Картинки складываются в
 * `app/build/screenshots/`.
 *
 * Время останавливаем: у подсветки кабинета бесконечная пульсация, и тест
 * иначе ждёт её вечно.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class MapScreenShotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun shoot(name: String, initialRoom: String?, fromRoom: String?) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MyApplicationTheme {
                CollegeMapScreen(initialRoom = initialRoom, fromRoom = fromRoom, onBack = {})
            }
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
    fun `карта из расписания — с маршрутом`() {
        // 101 и 128 на первом этаже, между ними размечены коридоры
        shoot("map-route", "128", "101")
    }

    @Test
    fun `карта из расписания без маршрута`() {
        shoot("map-room-only", "128", null)
    }
}
