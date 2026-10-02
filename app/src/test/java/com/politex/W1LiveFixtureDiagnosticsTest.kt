package com.politex

import com.politex.data.network.MpkScheduleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W1: регресс на ЖИВОЙ фикстуре 05.10.2026 — реальный бинарный .doc (OLE2, d0cf11e0)
 * с сайта, документ понедельника, который заказчик видит, а синк раньше не запрашивал.
 * Гипотеза «парсер не знает новый формат» опровергнута этим тестом: документ парсится
 * проверенной стратегией grid (doc-utf16).
 */
class W1LiveFixtureDiagnosticsTest {
    private fun liveDocBytes(): ByteArray =
        javaClass.getResourceAsStream("/fixtures/05-10-2026-raspisanie-uchashhihsya.doc")!!.readBytes()

    @Test
    fun `W1 фикстура понедельника - уроки реальных групп больше нуля`() {
        val expected = mapOf("41О" to 8, "41Г" to 9, "21Д" to 8, "11О" to 6)
        for ((group, count) in expected) {
            val lessons = MpkScheduleParser.parseFile(liveDocBytes(), targetGroup = group, targetDate = "05.10.2026")
            assertEquals("group=$group stats=${MpkScheduleParser.lastParseStats}", count, lessons.size)
            // Заголовок «РАСПИСАНИЕ на 05.10.2026 г. ПОНЕДЕЛЬНИК» — строки штампуются понедельником
            assertTrue(lessons.all { it.dateString == "05.10.2026" })
            assertTrue(lessons.all { it.dayOfWeek == 1 })
            assertTrue(lessons.all { it.timeStart.isNotBlank() })
        }
        // Проверенная стратегия не деградировала
        assertEquals("grid", MpkScheduleParser.lastParseStats.strategy)
        assertEquals("doc-utf16", MpkScheduleParser.lastParseStats.format)
    }

    @Test
    fun `W1 parseFileAllGroups на живой фикстуре разбирает все группы каталога`() {
        val all = MpkScheduleParser.parseFileAllGroups(
            liveDocBytes(),
            targetDate = "05.10.2026",
            candidateGroups = listOf("41О", "41Г", "21Д", "11О", "21М"),
        )
        assertEquals(setOf("41О", "41Г", "21Д", "11О", "21М"), all.map { it.groupName }.toSet())
        assertTrue(all.isNotEmpty())
    }
}
