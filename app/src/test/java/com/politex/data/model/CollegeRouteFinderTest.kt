package com.politex.data.model

import org.junit.Assert.assertEquals
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
}
