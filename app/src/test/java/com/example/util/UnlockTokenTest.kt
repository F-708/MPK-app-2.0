package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Проверка кода разблокировки: он считается из времени и не требует сервера. */
class UnlockTokenTest {

    /** 2026-09-16 12:00:00 UTC */
    private val noon = 1_789_531_200_000L

    @Test
    fun `код состоит из шести цифр`() {
        val code = UnlockToken.codeForMinute(UnlockToken.minuteIndex(noon))
        assertEquals(UnlockToken.DIGITS, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun `в одну минуту код одинаковый, в соседнюю — другой`() {
        val now = UnlockToken.minuteIndex(noon)
        assertEquals(UnlockToken.codeForMinute(now), UnlockToken.codeForMinute(now))
        assertNotEquals(UnlockToken.codeForMinute(now), UnlockToken.codeForMinute(now + 1))
    }

    @Test
    fun `текущий код принимается`() {
        val code = UnlockToken.currentCode(noon)
        assertTrue(UnlockToken.isValid(code, noon))
    }

    @Test
    fun `код прошлой минуты ещё принимается на границе`() {
        // Пользователь начал вводить в 11:59:58, подтвердил в 12:00:02
        val previous = UnlockToken.codeForMinute(UnlockToken.minuteIndex(noon) - 1)
        assertTrue(UnlockToken.isValid(previous, noon))
    }

    @Test
    fun `код двухминутной давности уже не принимается`() {
        val old = UnlockToken.codeForMinute(UnlockToken.minuteIndex(noon) - 2)
        assertFalse(UnlockToken.isValid(old, noon))
    }

    @Test
    fun `мусор и неверная длина не принимаются`() {
        assertFalse(UnlockToken.isValid("", noon))
        assertFalse(UnlockToken.isValid("12345", noon))
        assertFalse(UnlockToken.isValid("1234567", noon))
        assertFalse(UnlockToken.isValid("абвгде", noon))
    }

    @Test
    fun `секунды до смены кода убывают`() {
        assertEquals(60, UnlockToken.secondsUntilChange(noon))
        assertEquals(1, UnlockToken.secondsUntilChange(noon + 59_000L))
    }
}
