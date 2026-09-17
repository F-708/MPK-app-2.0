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

    @Test
    fun `метки кабинетов стоят на том же этаже, что и номер`() {
        val problems = CollegeMap.roomPins.filter { (room, pin) ->
            CollegeMap.floorForRoom(room) != pin.floor
        }
        assertEquals("Этаж метки расходится с номером кабинета: $problems", emptyMap<String, RoomPin>(), problems)
    }

    @Test
    fun `координаты меток лежат в пределах картинки`() {
        val broken = CollegeMap.roomPins.filter { (_, pin) ->
            pin.x !in 0f..1f || pin.y !in 0f..1f
        }
        assertEquals("Координаты вышли за пределы плана: $broken", emptyMap<String, RoomPin>(), broken)
    }
}
