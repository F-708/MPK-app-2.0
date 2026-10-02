package com.politex.data.model

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Интеграционные тесты мультиэтажной маршрутизации НА РЕАЛЬНЫХ ДАННЫХ college_map.json.
 * Ловят: рассинхрон схемы JSON/модели, разрывы графа после мерджа vision-разметки,
 * ошибки usable-фильтра лестниц (список заказчика: 1эт 131/115/136/147, 2эт 241/238/230,
 * 3эт 335/332/301/317, 4эт 401).
 *
 * Robolectric: боевой org.json (в чистом unit-тесте он не замокан) и загрузка из assets.
 */
@RunWith(RobolectricTestRunner::class)
class RealMapMultiFloorRouteTest {

    private val mapData: CollegeMapData by lazy {
        CollegeMapData.load(ApplicationProvider.getApplicationContext())
    }

    private fun floorsMap(): Map<String, FloorMap> =
        CollegeMap.Floor.entries.associate { it.id to mapData.floor(it.id) }

    private fun room(number: String): MapRoom {
        val floorId = CollegeMap.floorForRoom(number)?.id ?: error("$number: неизвестный этаж")
        return mapData.floor(floorId).room(number) ?: error("комната $number не найдена на этаже $floorId")
    }

    @Test
    fun `данные карты загружаются из ассета`() {
        // CollegeMapData.load глотает исключение → пустая карта; этот тест ловит такой регресс
        assertFalse("карта не должна быть пустой (ассет не загрузился?)", mapData.isEmpty)
        assertTrue("на 1 этаже должны быть комнаты", mapData.floor("1").rooms.isNotEmpty())
    }

    @Test
    fun `usable-разметка лестниц соответствует списку заказчика`() {
        val floors = floorsMap()
        val usableStairs = floors.values.flatMap { it.entrances }
            .filter { it.kind == "stairs" && it.usable }
        assertTrue("пригодных лестниц должно быть несколько", usableStairs.size >= 8)
        // Все лестницы имеют явное поле usable из JSON (схема v1 → usable:true по умолчанию)
        val allStairs = floors.values.flatMap { it.entrances }.filter { it.kind == "stairs" }
        assertTrue(allStairs.isNotEmpty())
        // Аварийные (118/223/320 ствол) помечены unusable
        val unusableIds = allStairs.filter { !it.usable }.map { it.id }
        assertTrue("ствол 118-223-320 должен быть unusable", unusableIds.containsAll(
            listOf("e_9kyra5x", "e_eztq0l0", "e_0jof8nz")))
    }

    @Test
    fun `маршрут 1 этаж - 2 этаж строится через пригодную лестницу`() {
        val r = CollegeRouteFinder.findMultiFloorRoute(room("131"), room("241"), floorsMap())
        assertNotNull("маршрут 131→241 должен строиться", r)
        assertTrue(r!!.crossesFloors)
        assertEquals(listOf("1", "2"), r.floorPaths.map { it.floorId })
        assertTrue(r.floorPaths.all { it.points.size >= 2 })
        assertTrue("дистанция положительная", r.distance > 0f)
    }

    @Test
    fun `маршрут 1 этаж - 3 этаж проходит через два этажа`() {
        val r = CollegeRouteFinder.findMultiFloorRoute(room("136"), room("332"), floorsMap())
        assertNotNull("маршрут 136→332 должен строиться", r)
        assertEquals(listOf("1", "2", "3-4"), r!!.floorPaths.map { it.floorId })
    }

    @Test
    fun `маршрут внутри одного этажа — один сегмент`() {
        val r = CollegeRouteFinder.findMultiFloorRoute(room("238"), room("241"), floorsMap())
        assertNotNull(r)
        assertFalse(r!!.crossesFloors)
        assertEquals(1, r.floorPaths.size)
    }

    @Test
    fun `маршрут по аварийной лестнице невозможен`() {
        // 118 (1эт) — ствол D, все три лестницы unusable. Комната 223 — его же ствол на 2 эт.
        // findMultiFloorRoute должен вернуть null (нет пригодной общей лестницы у этих мест),
        // либо построить путь через ДРУГУЮ пригодную лестницу — но старт/финиш у аварийного
        // ствола; проверяем что через сам аварийный ствол путь не идёт.
        val r = CollegeRouteFinder.findMultiFloorRoute(room("118"), room("223"), floorsMap())
        if (r != null) {
            val stairsUsed = r.floorPaths.flatMap { fp ->
                floorsMap()[fp.floorId]?.entrances
                    ?.filter { it.kind == "stairs" && !it.usable }
                    ?.filter { s -> fp.points.any { p -> Math.abs(p.x - s.x) < 1e-3f && Math.abs(p.y - s.y) < 1e-3f } }
                    ?.map { it.id } ?: emptyList()
            }
            assertTrue("аварийные лестницы не должны использоваться: $stairsUsed", stairsUsed.isEmpty())
        }
    }

    @Test
    fun `главный вход достижим как точка маршрута`() {
        val f1 = mapData.floor("1")
        val main = f1.entrances.firstOrNull { it.kind == "main" }
        assertNotNull("главный вход должен быть в данных", main)
        // Маршрут «от главного входа» (фаза 2.3: первая пара) — псевдо-комната с номером
        // 1-го этажа (floorForRoom по первой цифре) и координатами входа
        val startRoom = MapRoom(id = "entrance", number = "100", title = "", note = "",
            status = "normal", x = main!!.x, y = main.y, w = 0.01f, h = 0.01f)
        val r = CollegeRouteFinder.findMultiFloorRoute(startRoom, room("301"), floorsMap())
        assertNotNull("маршрут от главного входа до 301 должен строиться", r)
    }
}
