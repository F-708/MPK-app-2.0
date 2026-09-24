package com.politex

import com.politex.data.repository.isFullDayDocument
import com.politex.data.repository.shouldFetchDay
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Б3: два порога — «нужен ли сайт за днём» и «похож ли документ на дневное
 * расписание всего колледжа».
 *
 * Оба нужны, чтобы при недоступном сайте (VPN) приложение жило на том, что уже
 * разобрано, но при этом частичный документ не принимался за полный.
 */
class ScheduleCacheGateTest {

    private val catalog = 64

    // --- нужен ли поход в сеть ---

    @Test
    fun `день, разобранный по большинству групп, сайт не трогает`() {
        assertFalse(shouldFetchDay(alreadyParsedGroups = 40, catalogSize = catalog, force = false))
        assertFalse(shouldFetchDay(alreadyParsedGroups = 64, catalogSize = catalog, force = false))
    }

    @Test
    fun `пара групп из каталога — это ещё не разобранный день`() {
        // Регрессия: порог был «2 группы», и после ручного теста соединения
        // (2 группы из 64) кабинет навсегда показывал неполное расписание
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

    // --- можно ли принимать документ за «дневной» ---

    @Test
    fun `документ с большинством групп каталога принимается`() {
        assertTrue(isFullDayDocument(groupCount = 44, catalogSize = catalog))
        assertTrue(isFullDayDocument(groupCount = 32, catalogSize = catalog))
    }

    @Test
    fun `частичный документ не принимается — он затёр бы полные данные`() {
        // replaceForDate = DELETE(группа, дата) + INSERT: приняв такой документ,
        // мы бы стёрли уже лежащее полное расписание этих групп
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
}