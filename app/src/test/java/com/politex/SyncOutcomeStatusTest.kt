package com.politex

import com.politex.data.network.MpkNetworkClient
import com.politex.data.network.MpkScheduleParser
import com.politex.data.repository.SyncOutcome
import com.politex.data.repository.resolveSyncOutcome
import com.politex.data.repository.statusMessageFor
import com.politex.data.repository.toastFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class SyncOutcomeStatusTest {
    private val date = "06.10.2026"
    private val group = "41О"

    private fun liveDocBytes(): ByteArray =
        javaClass.getResourceAsStream("/fixtures/05-10-2026-raspisanie-uchashhihsya.doc")!!.readBytes()

    @Test
    fun `probe 404 - ни документа ни уроков - NotFound`() {
        assertEquals(
            SyncOutcome.NotFound,
            resolveSyncOutcome(groupLessons = 0, documentFound = false, targetDate = date),
        )
    }

    @Test
    fun `чужой документ date-mismatch - не принимается - NotFound`() {

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

    @Test
    fun `единственный промежуточный статус - Проверяю расписание`() {
        assertEquals("Проверяю расписание…", MpkNetworkClient.STATUS_CHECKING)
    }

    @Test
    fun `ни один финальный статус или тост не обещает скачивание`() {
        val messages = listOf(
            statusMessageFor(SyncOutcome.Ok(5)),
            statusMessageFor(SyncOutcome.FoundNoGroup(date)),
            statusMessageFor(SyncOutcome.NotFound),
            toastFor(SyncOutcome.Ok(5), group),
            toastFor(SyncOutcome.FoundNoGroup(date), group),
            toastFor(SyncOutcome.NotFound, group),
        )
        messages.forEach {
            assertFalse(it, it.contains("скачиваю", ignoreCase = true))
            assertFalse(it, it.contains("ищу", ignoreCase = true))
            assertFalse(it, it.contains("загружаем", ignoreCase = true))
            assertFalse(it, it.contains("обновляем", ignoreCase = true))
        }
    }

    @Test
    fun `статусы-обещания удалены из исходников синхронизации`() {
        listOf(
            "app/src/main/java/com/politex/data/repository/ScheduleRepository.kt",
            "app/src/main/java/com/politex/data/network/MpkNetworkClient.kt",
            "app/src/main/java/com/politex/ui/screens/GroupScheduleScreen.kt",
            "app/src/main/java/com/politex/ui/screens/ScheduleScreen.kt",
        ).forEach { rel ->
            val text = sourceText(rel)
            assertFalse(rel, text.contains("скачиваю", ignoreCase = true))
            assertFalse(rel, text.contains("Ищу на портале", ignoreCase = true))
            assertFalse(rel, text.contains("уже загружено", ignoreCase = true))
        }
    }

    private fun sourceText(rel: String): String {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, rel).exists()) {
            dir = dir.parentFile ?: error("исходник не найден: $rel")
        }
        return File(dir, rel).readText()
    }
}
