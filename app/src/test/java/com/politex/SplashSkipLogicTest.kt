package com.politex

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// N16: чистая логика skip-условия splash — приложение открыто из виджета/уведомления,
// если есть любой из extras (EXTRA_OPEN_TAB / EXTRA_ROOM / EXTRA_SCROLL_TARGET).
class SplashSkipLogicTest {

    @Test
    fun `splash пропускается при любом виджетовом extra`() {
        assertTrue(shouldSkipSplash(hasOpenTab = true, hasRoom = false, hasScrollTarget = false))
        assertTrue(shouldSkipSplash(hasOpenTab = false, hasRoom = true, hasScrollTarget = false))
        assertTrue(shouldSkipSplash(hasOpenTab = false, hasRoom = false, hasScrollTarget = true))
        assertTrue(shouldSkipSplash(hasOpenTab = true, hasRoom = true, hasScrollTarget = true))
    }

    @Test
    fun `splash проигрывается при обычном холодном старте без extras`() {
        assertFalse(shouldSkipSplash(hasOpenTab = false, hasRoom = false, hasScrollTarget = false))
    }
}
