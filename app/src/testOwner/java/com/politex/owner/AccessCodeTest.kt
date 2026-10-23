package com.politex.owner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessCodeTest {
    private val epoch = 1_760_000_015L
    private val stepStart = 1_760_000_010L

    @Test
    fun `генерация детерминирована для фиксированного времени`() {
        val first = AccessCode.generateCode(epoch)
        val second = AccessCode.generateCode(epoch)
        assertEquals(first, second)
        assertEquals(6, first.length)
        assertTrue(first.all { it.isDigit() })
    }

    @Test
    fun `код стабилен внутри 30-секундного окна`() {
        val expected = AccessCode.generateCode(stepStart)
        assertTrue((0L..29L).all { AccessCode.generateCode(stepStart + it) == expected })
    }

    @Test
    fun `код меняется при смене окна`() {
        assertNotEquals(AccessCode.generateCode(stepStart), AccessCode.generateCode(stepStart + 30))
        assertNotEquals(AccessCode.generateCode(stepStart), AccessCode.generateCode(stepStart - 1))
    }

    @Test
    fun `окно валидности - текущий шаг и соседние`() {
        val code = AccessCode.generateCode(epoch)
        assertTrue(AccessCode.isCodeValid(code, epoch))
        assertTrue(AccessCode.isCodeValid(code, stepStart))
        assertTrue(AccessCode.isCodeValid(code, epoch + 30))
        assertTrue(AccessCode.isCodeValid(code, epoch + 44))
        assertTrue(AccessCode.isCodeValid(code, epoch - 30))
        assertTrue(AccessCode.isCodeValid(code, epoch - 15))
    }

    @Test
    fun `код из окна плюс-минус два шага невалиден`() {
        val code = AccessCode.generateCode(epoch)
        assertFalse(AccessCode.isCodeValid(code, epoch + 60))
        assertFalse(AccessCode.isCodeValid(code, epoch + 90))
        assertFalse(AccessCode.isCodeValid(code, epoch - 60))
        assertFalse(AccessCode.isCodeValid(code, epoch - 90))
    }

    @Test
    fun `мусор не принимается`() {
        assertFalse(AccessCode.isCodeValid("", epoch))
        assertFalse(AccessCode.isCodeValid("12345", epoch))
        assertFalse(AccessCode.isCodeValid("1234567", epoch))
        assertFalse(AccessCode.isCodeValid("abcdef", epoch))
        assertFalse(AccessCode.isCodeValid("12 345", epoch))
        assertFalse(AccessCode.isCodeValid("12345-", epoch))
    }

    @Test
    fun `обёртка над часами устройства использует clock-seam`() {
        AccessClock.overrideSeconds = epoch
        try {
            assertEquals(AccessCode.generateCode(epoch), AccessCode.currentCode())
            assertTrue(AccessCode.isCurrentCodeValid(AccessCode.currentCode()))
            assertFalse(AccessCode.isCurrentCodeValid(AccessCode.generateCode(epoch + 120)))
        } finally {
            AccessClock.overrideSeconds = null
        }
    }

    @Test
    fun `остаток секунд в окне считается корректно`() {
        assertEquals(30, AccessCode.secondsLeftInStep(stepStart))
        assertEquals(25, AccessCode.secondsLeftInStep(epoch))
        assertEquals(1, AccessCode.secondsLeftInStep(stepStart + 29))
    }

    @Test
    fun `шаг времени округляется вниз включая отрицательное время`() {
        assertEquals(0L, AccessCode.timeStep(0L))
        assertEquals(0L, AccessCode.timeStep(29L))
        assertEquals(1L, AccessCode.timeStep(30L))
        assertEquals(-1L, AccessCode.timeStep(-1L))
        assertEquals(-1L, AccessCode.timeStep(-30L))
        assertEquals(-2L, AccessCode.timeStep(-31L))
    }
}
