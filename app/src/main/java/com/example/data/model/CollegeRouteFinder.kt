package com.example.data.model

/**
 * Поиск пути по этажу колледжа.
 *
 * Граф собирается из двух источников, которые размечает заказчик в редакторе:
 * - **узлы** — перекрёстки коридоров, у каждого есть связи с соседними узлами;
 * - **коридоры** — ломаные линии вдоль проходов; их вершины соединяются
 *   последовательно, а концы подвязываются к ближайшим узлам.
 *
 * Кабинет подключается к ближайшей вершине графа — считается, что из двери
 * кабинета выходишь в ближайший проход. Если граф пуст или точки не связаны,
 * маршрут не строится: приложение просто подсветит кабинет.
 */
object CollegeRouteFinder {

    /** Вершина графа. */
    private data class Vertex(val x: Float, val y: Float)

    /** Готовый маршрут: точки в долях от размера плана. */
    data class Route(val points: List<MapPoint>, val distance: Float)

    /** Насколько далеко конец коридора может «дотянуться» до узла, чтобы с ним слиться. */
    private const val JOIN_RADIUS = 0.05f

    fun findRoute(
        from: MapRoom,
        to: MapRoom,
        floor: FloorMap
    ): Route? {
        if (from.id == to.id) return null

        val vertices = mutableListOf<Vertex>()
        val adjacency = mutableListOf<MutableList<Pair<Int, Float>>>()

        fun addVertex(x: Float, y: Float): Int {
            // Одинаковые точки (коридор пришёл в узел) не дублируем
            vertices.forEachIndexed { i, v ->
                if (Math.abs(v.x - x) < 1e-4f && Math.abs(v.y - y) < 1e-4f) return i
            }
            vertices.add(Vertex(x, y))
            adjacency.add(mutableListOf())
            return vertices.size - 1
        }

        fun link(a: Int, b: Int) {
            if (a == b) return
            val d = dist(vertices[a], vertices[b])
            adjacency[a].add(b to d)
            adjacency[b].add(a to d)
        }

        // 1. Узлы и связи между ними
        val nodeIndex = HashMap<String, Int>()
        floor.nodes.forEach { n ->
            nodeIndex[n.id] = addVertex(n.x, n.y)
        }
        floor.nodes.forEach { n ->
            val a = nodeIndex[n.id] ?: return@forEach
            n.links.forEach { other -> nodeIndex[other]?.let { link(a, it) } }
        }

        // 2. Коридоры: вершины подряд, концы — к ближайшим узлам
        floor.corridors.forEach { c ->
            if (c.points.size < 2) return@forEach
            val idx = c.points.map { p -> addVertex(p.x, p.y) }
            for (i in 1 until idx.size) link(idx[i - 1], idx[i])

            listOf(idx.first(), idx.last()).forEach { end ->
                var best = -1
                var bestD = JOIN_RADIUS
                nodeIndex.values.forEach { ni ->
                    val d = dist(vertices[end], vertices[ni])
                    if (d < bestD) { bestD = d; best = ni }
                }
                if (best >= 0) link(end, best)
            }
        }

        if (vertices.isEmpty()) return null

        val start = nearestVertex(vertices, from.x, from.y)
        val finish = nearestVertex(vertices, to.x, to.y)
        if (start < 0 || finish < 0) return null

        val path = shortestPath(vertices, adjacency, start, finish) ?: return null

        // Линия: центр кабинета отправления, ВСЕ вершины пути, центр кабинета назначения.
        // Раньше первая и последняя вершины выбрасывались, и линия шла напрямую
        // через стены, минуя коридор.
        val points = mutableListOf(MapPoint(from.x, from.y))
        path.forEach { points.add(MapPoint(vertices[it].x, vertices[it].y)) }
        points.add(MapPoint(to.x, to.y))

        // Длину считаем по звеньям, без первого и последнего отрезка «от двери до графа»
        var total = 0f
        for (i in 1 until path.size) total += dist(vertices[path[i - 1]], vertices[path[i]])
        return Route(points, total)
    }

    private fun nearestVertex(vertices: List<Vertex>, x: Float, y: Float): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        vertices.forEachIndexed { i, v ->
            val d = (v.x - x) * (v.x - x) + (v.y - y) * (v.y - y)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    private fun dist(a: Vertex, b: Vertex): Float =
        Math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    /** Дейкстра: граф маленький, простая реализация без очереди с приоритетом достаточна. */
    private fun shortestPath(
        vertices: List<Vertex>,
        adjacency: List<List<Pair<Int, Float>>>,
        start: Int,
        finish: Int
    ): List<Int>? {
        val n = vertices.size
        val best = FloatArray(n) { Float.MAX_VALUE }
        val prev = IntArray(n) { -1 }
        val used = BooleanArray(n)
        best[start] = 0f

        repeat(n) {
            var v = -1
            var bestD = Float.MAX_VALUE
            for (i in 0 until n) {
                if (!used[i] && best[i] < bestD) { bestD = best[i]; v = i }
            }
            if (v < 0) return@repeat
            used[v] = true
            adjacency[v].forEach { (to, w) ->
                if (best[v] + w < best[to]) {
                    best[to] = best[v] + w
                    prev[to] = v
                }
            }
        }

        if (best[finish] == Float.MAX_VALUE) return null

        val path = ArrayDeque<Int>()
        var cur = finish
        while (cur >= 0) {
            path.addFirst(cur)
            if (cur == start) break
            cur = prev[cur]
        }
        return if (path.first() == start) path.toList() else null
    }
}
