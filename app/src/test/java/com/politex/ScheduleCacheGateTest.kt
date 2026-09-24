package com.politex

import com.politex.data.repository.shouldFetchDay
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Б3: гейт «нужен ли сайт за днём целиком».
 *
 * Смысл: дневной документ парсится сразу по всем группам колледжа за один проход,
 * поэтому если по дате уже есть строки у 2+ групп — день распарсен и повторный
 * поход на сайт не нужен. У заказчика это ровно тот случай, когда сайт недоступен
 * (VPN), а расписание чужой группы/кабинета всё равно должно открываться.
 */
class ScheduleCacheGateTest {

    @Test
    fun `день с двумя и более разобранными группами сайт не трогает`() {
        assertFalse(shouldFetchDay(alreadyParsedGroups = 2, catalogSize = 64, force = false))
        assertFalse(shouldFetchDay(alreadyParsedGroups = 40, catalogSize = 64, force = false))
    }

    @Test
    fun `день с нулём или одной группой требует загрузки`() {
        assertTrue(shouldFetchDay(alreadyParsedGroups = 0, catalogSize = 64, force = false))
        assertTrue(shouldFetchDay(alreadyParsedGroups = 1, catalogSize = 64, force = false))
    }

    @Test
    fun `force обходит гейт даже при полном кэше`() {
        assertTrue(shouldFetchDay(alreadyParsedGroups = 64, catalogSize = 64, force = true))
    }

    @Test
    fun `без каталога групп загрузка невозможна`() {
        assertFalse(shouldFetchDay(alreadyParsedGroups = 0, catalogSize = 0, force = false))
        assertFalse(shouldFetchDay(alreadyParsedGroups = 0, catalogSize = 0, force = true))
    }
}