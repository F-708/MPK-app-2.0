package com.example.data.repository

import java.util.Calendar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Проверка чистки архива расписания (ScheduleRepository.pruneOldLessons). */
class ScheduleArchiveTest {

    private fun ms(y: Int, m: Int, d: Int): Long = Calendar.getInstance().apply {
        clear()
        set(y, m - 1, d)
    }.timeInMillis

    private val cutoff = ms(2026, 1, 1)

    @Test
    fun `дата до отсечки считается устаревшей`() {
        assertTrue(isStaleDate("31.12.2025", cutoff))
        assertTrue(isStaleDate("01.09.2025", cutoff))
    }

    @Test
    fun `дата отсечки и позже остаётся`() {
        assertFalse(isStaleDate("01.01.2026", cutoff))
        assertFalse(isStaleDate("15.09.2026", cutoff))
    }

    @Test
    fun `не-даты не удаляются`() {
        // Уроки-шаблоны по дню недели хранятся с пустым dateString
        assertFalse(isStaleDate("", cutoff))
        assertFalse(isStaleDate("2025-09-01", cutoff))
        assertFalse(isStaleDate("1.9.2025", cutoff))
        assertFalse(isStaleDate("сегодня", cutoff))
    }
}
