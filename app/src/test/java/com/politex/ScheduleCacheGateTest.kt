package com.politex

import com.politex.data.local.entity.LessonEntity
import com.politex.data.repository.isFullDayDocument
import com.politex.data.repository.shouldFetchDay
import com.politex.data.repository.syncedCountForGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleCacheGateTest {
    private val catalog = 64

    @Test
    fun `день, разобранный по большинству групп, сайт не трогает`() {
        assertFalse(shouldFetchDay(alreadyParsedGroups = 40, catalogSize = catalog, force = false))
        assertFalse(shouldFetchDay(alreadyParsedGroups = 64, catalogSize = catalog, force = false))
    }

    @Test
    fun `пара групп из каталога — это ещё не разобранный день`() {

        assertTrue(shouldFetchDay(alreadyParsedGroups = 2, catalogSize = catalog, force = false))
        assertTrue(shouldFetchDay(alreadyParsedGroups = 31, catalogSize = catalog, force = false))
    }

    @Test
    fun `пустой день требует загрузки`() {
        assertTrue(shouldFetchDay(alreadyParsedGroups = 0, catalogSize = catalog, force = false))
    }

    @Test
    fun `force обходит гейт даже при полном кэше`() {
        assertTrue(shouldFetchDay(alreadyParsedGroups = 64, catalogSize = catalog, force = true))
    }

    @Test
    fun `без каталога групп загрузка невозможна`() {
        assertFalse(shouldFetchDay(alreadyParsedGroups = 0, catalogSize = 0, force = false))
        assertFalse(shouldFetchDay(alreadyParsedGroups = 0, catalogSize = 0, force = true))
    }

    @Test
    fun `документ с большинством групп каталога принимается`() {
        assertTrue(isFullDayDocument(groupCount = 44, catalogSize = catalog))
        assertTrue(isFullDayDocument(groupCount = 32, catalogSize = catalog))
    }

    @Test
    fun `частичный документ не принимается — он затёр бы полные данные`() {

        assertFalse(isFullDayDocument(groupCount = 1, catalogSize = catalog))
        assertFalse(isFullDayDocument(groupCount = 2, catalogSize = catalog))
        assertFalse(isFullDayDocument(groupCount = 31, catalogSize = catalog))
    }

    @Test
    fun `пустой документ не принимается`() {
        assertFalse(isFullDayDocument(groupCount = 0, catalogSize = catalog))
    }

    @Test
    fun `без каталога ничего не принимается`() {
        assertFalse(isFullDayDocument(groupCount = 44, catalogSize = 0))
    }

    private fun lesson(
        group: String,
        num: Int,
    ) = LessonEntity(
        groupName = group,
        dayOfWeek = 1,
        lessonNumber = num,
        timeStart = "08:15",
        timeEnd = "09:55",
        subjectRaw = "Тест",
        roomFirst = "204",
        teacherFirst = "Ковалев В.П.",
        dateString = "01.10.2026",
    )

    @Test
    fun `N — только уроки своей группы, а не всего документа`() {

        val doc = listOf(lesson("41О", 1), lesson("41О", 2), lesson("11Э", 1), lesson("31П", 1), lesson("31П", 2))
        assertEquals(2, syncedCountForGroup(doc, "41О"))
        assertEquals(1, syncedCountForGroup(doc, "11Э"))
        assertEquals(2, syncedCountForGroup(doc, "31П"))
    }

    @Test
    fun `группы нет в документе — N=0 (тост «новых данных пока нет», а не «N уроков»)`() {
        val doc = listOf(lesson("11Э", 1), lesson("31П", 1))
        assertEquals(0, syncedCountForGroup(doc, "41О"))
        assertEquals(0, syncedCountForGroup(emptyList(), "41О"))
    }
}
