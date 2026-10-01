package com.politex.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Мультиэтажная маршрутизация (Фаза 2.3). Граф синтетический, чтобы не зависеть от
 * vision-разметки реальных планов: каждый этаж — горизонтальный коридор y=0.5, лестницы
 * в одних и тех же x на всех этажах (как в реальном college_map.json, см. MASTER-PLAN).
 */
class CollegeMultiFloorRouteTest {

    private fun room(number: String, x: Float, y: Float) = MapRoom(
        id = number, number = number, title = "", note = "", status = "normal",
        x = x, y = y, w = 0.02f, h = 0.02f
    )

    private fun stair(id: String, x: Float, y: Float) =
        MapEntrance(id = id, x = x, y = y, kind = "stairs", dir = 0f, title = "Лестница")

    // Коридор y=0.5 с точками НА позициях узлов (0.25/0.75) — иначе концы коридора дальше
    // JOIN_RADIUS от узлов и граф получается несвязным (в реальном college_map.json точки
    // коридоров совпадают с узлами, см. buildGraph: совпадение координат → одна вершина).
    private fun floor(stairs: List<MapEntrance>) = FloorMap(
        corridors = listOf(
            MapCorridor("c", listOf(
                MapPoint(0.05f, 0.5f), MapPoint(0.25f, 0.5f),
                MapPoint(0.75f, 0.5f), MapPoint(0.95f, 0.5f)
            ), 0.012f)
        ),
        nodes = listOf(
            MapNode("nL", 0.25f, 0.5f, listOf("nR")),
            MapNode("nR", 0.75f, 0.5f, listOf("nL"))
        ),
        entrances = stairs
    )

    private fun floors() = mapOf(
        "1" to floor(listOf(stair("s1a", 0.25f, 0.5f), stair("s1b", 0.75f, 0.5f))),
        "2" to floor(listOf(stair("s2a", 0.25f, 0.5f), stair("s2b", 0.75f, 0.5f))),
        "3-4" to floor(listOf(stair("s3a", 0.25f, 0.5f), stair("s3b", 0.75f, 0.5f)))
    )

    @Test
    fun `один этаж — обычный маршрут, без лестниц`() {
        val r = CollegeRouteFinder.findMultiFloorRoute(
            room("101", 0.1f, 0.3f), room("105", 0.9f, 0.3f), floors()
        )
        assertNotNull(r)
        assertFalse("один этаж — не мультиэтажный", r!!.crossesFloors)
        assertEquals(1, r.floorPaths.size)
        assertEquals("1", r.floorPaths[0].floorId)
    }

    @Test
    fun `соседние этажи 1-2 идут через общую лестницу`() {
        val r = CollegeRouteFinder.findMultiFloorRoute(
            room("101", 0.1f, 0.3f), room("205", 0.9f, 0.3f), floors()
        )
        assertNotNull("маршрут 1→2 должен найтись", r)
        assertTrue(r!!.crossesFloors)
        assertEquals(listOf("1", "2"), r.floorPaths.map { it.floorId })
        // каждая лестница-точка входит в путь хотя бы одного этажа
        assertTrue(r.floorPaths.all { it.points.size >= 2 })
    }

    @Test
    fun `через два этажа 1-3 — три сегмента в правильном порядке`() {
        val r = CollegeRouteFinder.findMultiFloorRoute(
            room("101", 0.1f, 0.3f), room("305", 0.9f, 0.3f), floors()
        )
        assertNotNull(r)
        assertEquals(listOf("1", "2", "3-4"), r!!.floorPaths.map { it.floorId })
    }

    @Test
    fun `обратное направление 3-1 — этажи в убывающем порядке`() {
        val r = CollegeRouteFinder.findMultiFloorRoute(
            room("305", 0.9f, 0.3f), room("101", 0.1f, 0.3f), floors()
        )
        assertNotNull(r)
        assertEquals(listOf("3-4", "2", "1"), r!!.floorPaths.map { it.floorId })
    }

    @Test
    fun `аварийная лестница не используется`() {
        // Единственная лестница на 2 этаже помечена непригодной → маршрута 1→2 нет
        val f = mapOf(
            "1" to floor(listOf(stair("s1a", 0.25f, 0.5f))),
            "2" to floor(listOf(stair("s2a", 0.25f, 0.5f)))
        )
        val r = CollegeRouteFinder.findMultiFloorRoute(
            room("101", 0.1f, 0.3f), room("205", 0.9f, 0.3f), f,
            isUsableStair = { it.id != "s2a" } // лестница 2 этажа аварийная
        )
        assertNull("по аварийной лестнице маршрута быть не должно", r)
    }

    @Test
    fun `выбирается ближайшая к старту лестница`() {
        val r = CollegeRouteFinder.findMultiFloorRoute(
            room("101", 0.1f, 0.3f), // близко к x=0.25
            room("205", 0.9f, 0.3f),
            floors()
        )
        assertNotNull(r)
        // на 1 этаже путь должен идти к левой лестнице (x≈0.25), не к правой (x≈0.75)
        val f1 = r!!.floorPaths.first { it.floorId == "1" }
        val lastOnF1 = f1.points.last()
        assertTrue("старт у x=0.1 должен вести к лестнице x≈0.25, получилось ${lastOnF1.x}",
            lastOnF1.x < 0.5f)
    }

    @Test
    fun `несуществующий этаж — null`() {
        val r = CollegeRouteFinder.findMultiFloorRoute(
            room("101", 0.1f, 0.3f), room("905", 0.9f, 0.3f), floors() // 9 этаж не в CollegeMap
        )
        assertNull(r)
    }

    @Test
    fun `points плоский список содержит старт и финиш`() {
        val r = CollegeRouteFinder.findMultiFloorRoute(
            room("101", 0.1f, 0.3f), room("205", 0.9f, 0.3f), floors()
        )
        assertNotNull(r)
        val all = r!!.points
        assertTrue(all.size >= 4)
    }
}
