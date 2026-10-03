package com.politex.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollegeRouteFinderTest {
    private fun room(
        id: String,
        x: Float,
        y: Float,
    ) = MapRoom(
        id = id,
        number = id,
        title = "",
        note = "",
        status = "normal",
        x = x,
        y = y,
        w = 0.02f,
        h = 0.02f,
    )

    private fun floorWithCorridor() = FloorMap(
        corridors =
        listOf(
            MapCorridor("c1", listOf(MapPoint(0.1f, 0.5f), MapPoint(0.9f, 0.5f)), 0.012f),
        ),
    )

    @Test
    fun `путь строится вдоль коридора`() {
        val f = floorWithCorridor()
        val a = room("101", 0.1f, 0.3f)
        val b = room("105", 0.9f, 0.3f)

        val route = CollegeRouteFinder.findRoute(a, b, f)

        assertNotNull("маршрут должен найтись", route)

        assertTrue(route!!.points.first().x == a.x && route.points.first().y == a.y)
        assertTrue(route.points.last().x == b.x && route.points.last().y == b.y)
        assertTrue("в пути должна быть хотя бы одна точка графа", route.points.size >= 3)
    }

    @Test
    fun `без разметки маршрута нет`() {
        val empty = FloorMap()
        assertNull(CollegeRouteFinder.findRoute(room("101", 0.1f, 0.1f), room("105", 0.9f, 0.9f), empty))
    }

    @Test
    fun `в один и тот же кабинет маршрут не строим`() {
        val f = floorWithCorridor()
        val a = room("101", 0.1f, 0.3f)
        assertNull(CollegeRouteFinder.findRoute(a, a, f))
    }

    @Test
    fun `несвязанные коридоры пути не дают`() {
        val f =
            FloorMap(
                corridors =
                listOf(
                    MapCorridor("c1", listOf(MapPoint(0.05f, 0.1f), MapPoint(0.1f, 0.1f)), 0.012f),
                    MapCorridor("c2", listOf(MapPoint(0.9f, 0.9f), MapPoint(0.95f, 0.9f)), 0.012f),
                ),
            )
        val route = CollegeRouteFinder.findRoute(room("101", 0.07f, 0.1f), room("105", 0.92f, 0.9f), f)

        assertNull("маршрут через несвязанные коридоры строить нельзя", route)
    }

    @Test
    fun `узлы связывают коридоры в один путь`() {
        val f =
            FloorMap(
                corridors =
                listOf(
                    MapCorridor("c1", listOf(MapPoint(0.05f, 0.5f), MapPoint(0.5f, 0.5f)), 0.012f),
                    MapCorridor("c2", listOf(MapPoint(0.5f, 0.5f), MapPoint(0.95f, 0.5f)), 0.012f),
                ),
                nodes = listOf(MapNode("n1", 0.5f, 0.5f, emptyList())),
            )
        val route = CollegeRouteFinder.findRoute(room("101", 0.05f, 0.3f), room("105", 0.95f, 0.3f), f)
        assertNotNull("через общий узел маршрут должен строиться", route)
    }

    @Test
    fun `длина маршрута считается по графу`() {
        val f = floorWithCorridor()
        val route = CollegeRouteFinder.findRoute(room("101", 0.1f, 0.5f), room("105", 0.9f, 0.5f), f)
        assertNotNull(route)

        assertTrue(
            "длина около 0.8, получилось ${route!!.distance}",
            route.distance > 0.7f && route.distance < 0.9f,
        )
    }

    // W40a: комната цепляется ПРОЕКЦИЕЙ двери на ближайший сегмент коридора (без радиуса),
    // а не к дальнему узлу — маршрут из двери выходит в коридор сразу, без крюка.
    @Test
    fun `W40a - комната цепляется проекцией двери на коридор без крюка к узлу`() {
        val f = floorWithCorridor() // коридор (0.1,0.5)-(0.9,0.5): вершины-концы в 0.4 от дверей
        val a = room("101", 0.5f, 0.3f).let { it.copy(door = MapPoint(0.5f, 0.46f)) }
        val b = room("105", 0.5f, 0.7f).let { it.copy(door = MapPoint(0.5f, 0.54f)) }

        val route = CollegeRouteFinder.findRoute(a, b, f)

        assertNotNull("маршрут должен найтись", route)
        // путь проходит через точку проекции (0.5,0.5) — перпендикуляр из обеих дверей
        assertTrue(
            "в пути должна быть точка проекции (0.5,0.5), путь ${route!!.points}",
            route.points.any { Math.abs(it.x - 0.5f) < 1e-3f && Math.abs(it.y - 0.5f) < 1e-3f },
        )
        // и НЕ ходит к концам коридора (крюк к дальнему узлу)
        assertTrue(
            "путь не должен идти к концам коридора (крюк), путь ${route.points}",
            route.points.none {
                (Math.abs(it.x - 0.1f) < 1e-3f && Math.abs(it.y - 0.5f) < 1e-3f) ||
                    (Math.abs(it.x - 0.9f) < 1e-3f && Math.abs(it.y - 0.5f) < 1e-3f)
            },
        )
        // длина по прямой через коридор, а не 2×0.4+0.8 крюком через конец
        assertTrue("длина должна быть короткой, получилось ${route.distance}", route.distance < 0.5f)
    }

    // W40b: две двери на ОДНОМ длинном сегменте «на глаз»-коридора связаны цепочкой
    // проекций по t — маршрут идёт вдоль коридора, не кидаясь к дальним концам сегмента.
    @Test
    fun `W40b - две двери на одном сегменте идут вдоль коридора`() {
        val f = floorWithCorridor() // один длинный сегмент (0.1,0.5)-(0.9,0.5)
        val a = room("101", 0.3f, 0.3f).let { it.copy(door = MapPoint(0.3f, 0.46f)) }
        val b = room("105", 0.7f, 0.3f).let { it.copy(door = MapPoint(0.7f, 0.46f)) }

        val route = CollegeRouteFinder.findRoute(a, b, f)

        assertNotNull(route)
        // центры добавляют по 0.16 подхода к двери: длина ≈ 0.16+0.04+0.4+0.04+0.16 = 0.8
        assertTrue("длина вдоль коридора ~0.8, получилось ${route!!.distance}", route.distance in 0.7f..0.9f)
        // концы сегмента не посещаются — нет крюка к дальним узлам
        assertTrue(
            "путь не должен касаться концов сегмента, путь ${route.points}",
            route.points.none {
                (Math.abs(it.x - 0.1f) < 1e-3f && Math.abs(it.y - 0.5f) < 1e-3f) ||
                    (Math.abs(it.x - 0.9f) < 1e-3f && Math.abs(it.y - 0.5f) < 1e-3f)
            },
        )
    }

    // ---- W39c: ортогонализация маршрута для отрисовки ----

    @Test
    fun `ортогонализация - диагональ заменяется L-уголком`() {
        val out = CollegeRouteFinder.orthogonalize(listOf(MapPoint(0.1f, 0.1f), MapPoint(0.5f, 0.4f)), emptyList())
        assertEquals("диагональ → 3 точки (уголок)", 3, out.size)
        assertEquals(MapPoint(0.1f, 0.1f), out[0])
        assertEquals(MapPoint(0.5f, 0.4f), out[2])
        assertEquals("уголок c1: сначала вертикаль", MapPoint(0.1f, 0.4f), out[1])
    }

    @Test
    fun `ортогонализация - уголок выбирается мимо комнаты`() {
        // комната (x 0.05-0.15, y 0.2-0.3) перекрывает вертикальную ногу уголка c1=(0.1,0.4)
        val blocker =
            MapRoom(
                id = "r", number = "105", title = "", note = "", status = "normal",
                x = 0.1f, y = 0.25f, w = 0.1f, h = 0.1f,
            )
        val out = CollegeRouteFinder.orthogonalize(
            listOf(MapPoint(0.1f, 0.1f), MapPoint(0.5f, 0.4f)),
            listOf(blocker),
        )
        assertEquals(3, out.size)
        assertEquals("выбран уголок c2 мимо комнаты", MapPoint(0.5f, 0.1f), out[1])
        assertEquals(MapPoint(0.5f, 0.4f), out[2])
    }

    @Test
    fun `ортогонализация - комнаты концов сегмента не блокируют`() {
        // старт — центр комнаты: первая нога неизбежно проходит сквозь её прямоугольник,
        // но это СВОЯ комната — уголок допустим
        val startRoom =
            MapRoom(
                id = "a", number = "101", title = "", note = "", status = "normal",
                x = 0.1f, y = 0.1f, w = 0.06f, h = 0.06f,
            )
        val out = CollegeRouteFinder.orthogonalize(
            listOf(MapPoint(0.1f, 0.1f), MapPoint(0.5f, 0.4f)),
            listOf(startRoom),
        )
        assertEquals(3, out.size)
        assertEquals(MapPoint(0.1f, 0.4f), out[1])
    }

    @Test
    fun `ортогонализация - оба уголка через комнаты, диагональ НЕ остаётся (меньший пробой)`() {
        // две комнаты блокируют обе пары ног: Y7 запрещает диагональ вовсе —
        // берётся уголок с МЕНЬШИМ числом пробоев (здесь равны → c1)
        val blockers =
            listOf(
                MapRoom(id = "r1", number = "301", title = "", note = "", status = "normal", x = 0.1f, y = 0.25f, w = 0.1f, h = 0.1f), // нога c1
                MapRoom(id = "r2", number = "302", title = "", note = "", status = "normal", x = 0.5f, y = 0.25f, w = 0.1f, h = 0.1f), // нога c2 (x 0.45-0.55)
            )
        val out = CollegeRouteFinder.orthogonalize(
            listOf(MapPoint(0.1f, 0.1f), MapPoint(0.5f, 0.4f)),
            blockers,
        )
        assertEquals("диагонали нет — уголок с меньшим пробоем", 3, out.size)
        assertEquals(MapPoint(0.1f, 0.4f), out[1])
    }

    // Y7: дрожание разметки (уклон < ~19° в plan-px) защёлкивается на ось — итог строго осевой
    @Test
    fun `Y7 ортогонализация - все сегменты осевые в plan-px (снап дрожания)`() {
        val pts =
            listOf(
                MapPoint(0.1f, 0.5f),
                MapPoint(0.5f, 0.502f), // уклон 0.002*768=1.5px на 550px — дрожание
                MapPoint(0.52f, 0.2f), // уклон по x: 27px на 232px — почти вертикаль
            )
        val out = CollegeRouteFinder.orthogonalize(pts, emptyList())
        assertEquals(3, out.size)
        for (i in 1 until out.size) {
            val dx = Math.abs(out[i].x - out[i - 1].x) * CollegeRouteFinder.PLAN_ASPECT
            val dy = Math.abs(out[i].y - out[i - 1].y)
            assertTrue("сегмент $i (${out[i - 1]}→${out[i]}) должен быть осевым в plan-px", dx < 1e-3f || dy < 1e-3f)
        }
        assertEquals("горизонталь защёлкнута на y старта", 0.5f, out[1].y, 1e-6f)
        assertEquals("вертикаль защёлкнута на x предыдущей", 0.5f, out[2].x, 1e-6f)
    }

    // Y7: avoidRooms — осевой сегмент сквозь кабинет сдвигается на ближайшую свободную ось коридора
    @Test
    fun `Y7 avoidRooms - пробитый осевой сегмент обходится по оси соседнего коридора`() {
        val blocker =
            MapRoom(id = "B", number = "999", title = "", note = "", status = "normal", x = 0.5f, y = 0.5f, w = 0.2f, h = 0.1f)
        val corridors =
            listOf(
                MapCorridor("c1", listOf(MapPoint(0.1f, 0.5f), MapPoint(0.9f, 0.5f)), 0.012f), // пробит блокером
                MapCorridor("c2", listOf(MapPoint(0.1f, 0.7f), MapPoint(0.9f, 0.7f)), 0.012f), // свободная ось
            )
        val out =
            CollegeRouteFinder.avoidRooms(
                listOf(MapPoint(0.2f, 0.5f), MapPoint(0.8f, 0.5f)),
                listOf(blocker),
                corridors,
            )
        assertEquals("обход: a, c1, c2, b", 4, out.size)
        assertEquals(MapPoint(0.2f, 0.7f), out[1])
        assertEquals(MapPoint(0.8f, 0.7f), out[2])
        val bRect = floatArrayOf(0.4f, 0.45f, 0.6f, 0.55f)
        for (i in 1 until out.size) {
            assertFalse(
                "нога $i не должна пробивать блокер",
                CollegeRouteFinder.segCrossesRect(out[i - 1].x, out[i - 1].y, out[i].x, out[i].y, bRect),
            )
        }
    }

    // Y7: коннект к двери — первая/последняя точка мультиэтажного маршрута = дверь комнаты
    @Test
    fun `Y7 коннект - маршрут стартует и финиширует ровно в двери`() {
        val f = floorWithCorridor()
        val a = room("101", 0.2f, 0.3f).copy(door = MapPoint(0.2f, 0.46f))
        val b = room("105", 0.8f, 0.3f).copy(door = MapPoint(0.8f, 0.46f))
        val floors = mapOf("1" to f)
        val r = CollegeRouteFinder.findMultiFloorRoute(a, b, floors)
        assertNotNull(r)
        val first = r!!.floorPaths.first().points.first()
        val last = r.floorPaths.last().points.last()
        assertEquals(a.door!!.x, first.x, 1e-4f)
        assertEquals(a.door!!.y, first.y, 1e-4f)
        assertEquals(b.door!!.x, last.x, 1e-4f)
        assertEquals(b.door!!.y, last.y, 1e-4f)
    }

    // Y7: гарантия «не сквозь кабинеты» — ни одна нога итоговой полилинии не пробивает чужую комнату
    @Test
    fun `Y7 обход - полилиния маршрута не пробивает чужие кабинеты (синтетика)`() {
        // коридор y=0.5; кабинет-блокер нависает над коридором между дверями:
        // прямой проход вдоль коридора чист, но диагональ старт→финиш пробила бы блокер
        val corridor = MapCorridor("c", listOf(MapPoint(0.1f, 0.5f), MapPoint(0.9f, 0.5f)), 0.012f)
        val blocker =
            MapRoom(id = "B", number = "999", title = "", note = "", status = "normal", x = 0.5f, y = 0.35f, w = 0.3f, h = 0.2f)
        val a = MapRoom(id = "A", number = "101", title = "", note = "", status = "normal", x = 0.3f, y = 0.2f, w = 0.04f, h = 0.04f, door = MapPoint(0.3f, 0.46f))
        val b = MapRoom(id = "C", number = "105", title = "", note = "", status = "normal", x = 0.7f, y = 0.2f, w = 0.04f, h = 0.04f, door = MapPoint(0.7f, 0.46f))
        val floor = FloorMap(rooms = listOf(blocker, a, b), corridors = listOf(corridor))
        val r = CollegeRouteFinder.findMultiFloorRoute(a, b, mapOf("1" to floor))
        assertNotNull(r)
        val poly = CollegeRouteFinder.routePolyline(r!!.floorPaths[0].points, floor)
        val bRect = floatArrayOf(0.35f, 0.25f, 0.65f, 0.45f)
        for (i in 1 until poly.size) {
            assertFalse(
                "нога $i (${poly[i - 1]}→${poly[i]}) не должна пробивать блокер B",
                CollegeRouteFinder.segCrossesRect(poly[i - 1].x, poly[i - 1].y, poly[i].x, poly[i].y, bRect),
            )
        }
    }

    @Test
    fun `ортогонализация - уже ортогональные сегменты не трогаются`() {
        val pts = listOf(MapPoint(0.1f, 0.1f), MapPoint(0.5f, 0.1f), MapPoint(0.5f, 0.4f))
        assertEquals(pts, CollegeRouteFinder.orthogonalize(pts, emptyList()))
    }

    // ---- X26: segCrossesRect (Liang–Barsky) + чистая проекция attach ----

    @Test
    fun `X26 segCrossesRect Liang-Barsky`() {
        val r = floatArrayOf(0.4f, 0.4f, 0.6f, 0.6f)
        assertTrue("вертикаль сквозь rect", CollegeRouteFinder.segCrossesRect(0.5f, 0.3f, 0.5f, 0.7f, r))
        assertTrue("горизонталь сквозь rect", CollegeRouteFinder.segCrossesRect(0.3f, 0.5f, 0.7f, 0.5f, r))
        assertTrue("диагональ сквозь rect", CollegeRouteFinder.segCrossesRect(0.3f, 0.3f, 0.7f, 0.7f, r))
        assertFalse("отрезок далеко снаружи", CollegeRouteFinder.segCrossesRect(0.1f, 0.1f, 0.2f, 0.2f, r))
        assertFalse("отрезок снаружи справа", CollegeRouteFinder.segCrossesRect(0.7f, 0.7f, 0.9f, 0.9f, r))
        assertFalse("отрезок слева, в rect не входит", CollegeRouteFinder.segCrossesRect(0.3f, 0.65f, 0.35f, 0.65f, r))
    }

    @Test
    fun `X26 attach проекция огибает чужую комнату - без пробоя стены`() {
        // Коридор y=0.5 из НЕСКОЛЬКИХ сегментов. Комната A(0.5,0.8) door=null. Блокер B —
        // узкая стена прямо под A: ближайшая проекция (0.5,0.5) пробила бы B, но соседняя
        // (0.4,0.5) чистая → X26 выбирает её, и ни одна нога маршрута не пересекает B.
        val corridor =
            MapCorridor(
                "c",
                listOf(MapPoint(0.2f, 0.5f), MapPoint(0.4f, 0.5f), MapPoint(0.6f, 0.5f), MapPoint(0.8f, 0.5f)),
                0.012f,
            )
        val blocker =
            MapRoom(id = "B", number = "999", title = "", note = "", status = "normal", x = 0.5f, y = 0.65f, w = 0.04f, h = 0.1f)
        val a = MapRoom(id = "A", number = "101", title = "", note = "", status = "normal", x = 0.5f, y = 0.8f, w = 0.02f, h = 0.02f)
        val c =
            MapRoom(
                id = "C", number = "105", title = "", note = "", status = "normal",
                x = 0.75f, y = 0.8f, w = 0.02f, h = 0.02f, door = MapPoint(0.75f, 0.54f),
            )
        val floor = FloorMap(rooms = listOf(blocker, a, c), corridors = listOf(corridor))

        val route = CollegeRouteFinder.findRoute(a, c, floor)

        assertNotNull("маршрут A→C должен строиться", route)
        val bRect = floatArrayOf(0.48f, 0.6f, 0.52f, 0.7f)
        for (i in 1 until route!!.points.size) {
            val p0 = route.points[i - 1]
            val p1 = route.points[i]
            assertFalse(
                "нога $i ($p0→$p1) не должна пробивать блокер B",
                CollegeRouteFinder.segCrossesRect(p0.x, p0.y, p1.x, p1.y, bRect),
            )
        }
    }

    @Test
    fun `X26 ортогонализация - оба уголка blocked, берётся уголок с меньшим пробоем`() {
        val a = MapPoint(0.1f, 0.1f)
        val b = MapPoint(0.5f, 0.5f)
        // c1=(0.1,0.5): вертикаль x=0.1 пробивает b1 + горизонталь y=0.5 пробивает b2 → 2 пробоя.
        // c2=(0.5,0.1): горизонталь y=0.1 чиста + вертикаль x=0.5 пробивает b3 → 1 пробой.
        // диагональ a→b пробивает d → 1 пробой (НЕ чище c2) → выбирается уголок c2.
        fun r(id: String, x: Float, y: Float, w: Float, h: Float) =
            MapRoom(id = id, number = id, title = "", note = "", status = "normal", x = x, y = y, w = w, h = h)
        val blockers =
            listOf(
                r("b1", 0.1f, 0.25f, 0.04f, 0.1f), // [0.08,0.20,0.12,0.30] — вертикаль c1
                r("b2", 0.35f, 0.5f, 0.1f, 0.04f), // [0.30,0.48,0.40,0.52] — горизонталь c1
                r("b3", 0.5f, 0.25f, 0.04f, 0.1f), // [0.48,0.20,0.52,0.30] — вертикаль c2
                r("d", 0.3f, 0.3f, 0.04f, 0.04f), // [0.28,0.28,0.32,0.32] — диагональ
            )
        val out = CollegeRouteFinder.orthogonalize(listOf(a, b), blockers)
        assertEquals(3, out.size)
        assertEquals("выбран уголок c2 (1 пробой < 2 у c1, диагональ не чище)", MapPoint(0.5f, 0.1f), out[1])
    }
}
