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
    fun `ортогонализация - оба уголка через комнаты, остаётся диагональ`() {
        // две комнаты блокируют обе пары ног
        val blockers =
            listOf(
                MapRoom(id = "r1", number = "301", title = "", note = "", status = "normal", x = 0.1f, y = 0.25f, w = 0.1f, h = 0.1f), // нога c1
                MapRoom(id = "r2", number = "302", title = "", note = "", status = "normal", x = 0.5f, y = 0.25f, w = 0.1f, h = 0.1f), // нога c2 (x 0.45-0.55)
            )
        val out = CollegeRouteFinder.orthogonalize(
            listOf(MapPoint(0.1f, 0.1f), MapPoint(0.5f, 0.4f)),
            blockers,
        )
        assertEquals("оба зла — диагональ остаётся", 2, out.size)
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
