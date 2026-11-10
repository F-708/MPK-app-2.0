package com.politex.weather

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/** Окна сезонов: Хэллоуин 25.10–1.11, Новый год 22.12–8.01. */
class SeasonTest {

    @Test
    fun `Хэллоуин начинается 25 октября`() {
        assertEquals(Season.HALLOWEEN, seasonForDate(Calendar.OCTOBER, 25))
        assertEquals(Season.HALLOWEEN, seasonForDate(Calendar.OCTOBER, 31))
    }

    @Test
    fun `Хэллоуин заканчивается 1 ноября`() {
        assertEquals(Season.HALLOWEEN, seasonForDate(Calendar.NOVEMBER, 1))
        assertEquals(Season.NONE, seasonForDate(Calendar.NOVEMBER, 2))
    }

    @Test
    fun `до Хэллоуина сезона нет`() {
        assertEquals(Season.NONE, seasonForDate(Calendar.OCTOBER, 24))
    }

    @Test
    fun `Новый год начинается 22 декабря`() {
        assertEquals(Season.NEW_YEAR, seasonForDate(Calendar.DECEMBER, 22))
        assertEquals(Season.NEW_YEAR, seasonForDate(Calendar.DECEMBER, 31))
    }

    @Test
    fun `Новый год длится до 8 января`() {
        assertEquals(Season.NEW_YEAR, seasonForDate(Calendar.JANUARY, 1))
        assertEquals(Season.NEW_YEAR, seasonForDate(Calendar.JANUARY, 8))
        assertEquals(Season.NONE, seasonForDate(Calendar.JANUARY, 9))
    }

    @Test
    fun `до новогоднего окна сезона нет`() {
        assertEquals(Season.NONE, seasonForDate(Calendar.DECEMBER, 21))
    }

    @Test
    fun `обычная весенняя дата без сезона`() {
        assertEquals(Season.NONE, seasonForDate(Calendar.MARCH, 15))
    }

    @Test
    fun `OFF гасит сезон в сцене`() {
        val scene = resolveWeatherScene(
            effects = EffectsMode.OFF,
            sky = SkyMode.AUTO,
            snapshot = null,
            darkTheme = false,
            animationsAllowed = true,
            season = Season.HALLOWEEN,
        )
        assertEquals(Season.NONE, scene.season)
    }

    @Test
    fun `сезон проходит в сцену при включённых эффектах`() {
        val scene = resolveWeatherScene(
            effects = EffectsMode.AUTO,
            sky = SkyMode.AUTO,
            snapshot = null,
            darkTheme = false,
            animationsAllowed = true,
            season = Season.NEW_YEAR,
        )
        assertEquals(Season.NEW_YEAR, scene.season)
    }
}
