package com.politex

import com.politex.data.network.MpkScheduleParser
import com.politex.data.repository.SyncOutcome
import com.politex.data.repository.resolveSyncOutcome
import com.politex.data.repository.statusMessageFor
import com.politex.data.repository.toastFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Y1: честность формулировок статуса синка по сценариям.
 *  - probe 404 / чужой документ (date-mismatch) → «Расписание ещё не опубликовано»;
 *  - документ принят, но группы пользователя нет → «опубликовано, но вашей группы … нет»;
 *  - ok → «обновлено / загружено N уроков».
 *
 * `documentFound` в [resolveSyncOutcome] — это «принят документ, ПРОШЕДШИЙ date-валидацию»
 * (`MpkScheduleParser.documentMatchesDate`), поэтому чужой/просроченный документ даёт
 * documentFound=false → NotFound, а не ложное «найдено».
 */
class SyncOutcomeStatusTest {
    private val date = "06.10.2026"
    private val group = "41О"

    private fun liveDocBytes(): ByteArray =
        javaClass.getResourceAsStream("/fixtures/05-10-2026-raspisanie-uchashhihsya.doc")!!.readBytes()

    // --- сигналы → исход ---

    @Test
    fun `probe 404 - ни документа ни уроков - NotFound`() {
        assertEquals(
            SyncOutcome.NotFound,
            resolveSyncOutcome(groupLessons = 0, documentFound = false, targetDate = date),
        )
    }

    @Test
    fun `чужой документ date-mismatch - не принимается - NotFound`() {
        // Реальный документ 05.10 НЕ проходит date-валидацию для 06.10 → водопад его
        // отклоняет → documentFound=false → «не опубликовано», а не «найдено».
        assertFalse(MpkScheduleParser.documentMatchesDate(liveDocBytes(), date))
        assertEquals(
            SyncOutcome.NotFound,
            resolveSyncOutcome(groupLessons = 0, documentFound = false, targetDate = date),
        )
    }

    @Test
    fun `документ принят но группы нет - FoundNoGroup с датой`() {
        assertEquals(
            SyncOutcome.FoundNoGroup(date),
            resolveSyncOutcome(groupLessons = 0, documentFound = true, targetDate = date),
        )
    }

    @Test
    fun `документ разобран и группа есть - Ok с числом уроков`() {
        val outcome = resolveSyncOutcome(groupLessons = 5, documentFound = true, targetDate = date)
        assertEquals(SyncOutcome.Ok(5), outcome)
        assertEquals(5, outcome.count)
    }

    // --- строки статуса (diagnosticInfo / empty-state) ---

    @Test
    fun `статус NotFound - ещё не опубликовано`() {
        assertEquals("Расписание ещё не опубликовано", statusMessageFor(SyncOutcome.NotFound))
    }

    @Test
    fun `статус FoundNoGroup - опубликовано но вашей группы нет`() {
        assertEquals(
            "Расписание на $date опубликовано, но вашей группы в нём пока нет",
            statusMessageFor(SyncOutcome.FoundNoGroup(date)),
        )
    }

    @Test
    fun `статус Ok - загружено N уроков (склонение)`() {
        assertEquals("Расписание загружено (5 уроков)", statusMessageFor(SyncOutcome.Ok(5)))
        assertEquals("Расписание загружено (1 урок)", statusMessageFor(SyncOutcome.Ok(1)))
        assertEquals("Расписание загружено (3 урока)", statusMessageFor(SyncOutcome.Ok(3)))
    }

    // --- строки тоста (AppViewModel) ---

    @Test
    fun `тост Ok - обновлено N уроков`() {
        assertEquals(
            "Расписание группы $group обновлено (5 уроков)",
            toastFor(SyncOutcome.Ok(5), group),
        )
    }

    @Test
    fun `тост FoundNoGroup - опубликовано но вашей группы нет`() {
        assertEquals(
            "Расписание на $date опубликовано, но вашей группы ($group) в нём пока нет",
            toastFor(SyncOutcome.FoundNoGroup(date), group),
        )
    }

    @Test
    fun `тост NotFound - ещё не опубликовано`() {
        assertEquals("Расписание ещё не опубликовано", toastFor(SyncOutcome.NotFound, group))
    }
}
