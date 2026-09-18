package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Этаж по номеру кабинета: 1xx — первый, 2xx — второй, 3xx и 4xx — общий план. */
class CollegeMapTest {

    @Test
    fun `первая цифра номера задаёт этаж`() {
        assertEquals(CollegeMap.Floor.FIRST, CollegeMap.floorForRoom("101"))
        assertEquals(CollegeMap.Floor.FIRST, CollegeMap.floorForRoom("155"))
        assertEquals(CollegeMap.Floor.SECOND, CollegeMap.floorForRoom("214"))
        assertEquals(CollegeMap.Floor.SECOND, CollegeMap.floorForRoom("230"))
        assertEquals(CollegeMap.Floor.THIRD_FOURTH, CollegeMap.floorForRoom("312"))
        assertEquals(CollegeMap.Floor.THIRD_FOURTH, CollegeMap.floorForRoom("405"))
    }

    @Test
    fun `пробелы и мусор вокруг номера не мешают`() {
        assertEquals(CollegeMap.Floor.SECOND, CollegeMap.floorForRoom(" 214 "))
        assertEquals(CollegeMap.Floor.SECOND, CollegeMap.floorForRoom("214а"))
    }

    @Test
    fun `нестандартные кабинеты не привязываются к этажу наугад`() {
        // Лучше показать все планы, чем отправить не на тот этаж
        assertNull(CollegeMap.floorForRoom(""))
        assertNull(CollegeMap.floorForRoom("спортзал"))
        assertNull(CollegeMap.floorForRoom("актовый зал"))
        assertNull(CollegeMap.floorForRoom("914"))
    }
}
