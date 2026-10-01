package com.politex.data.model

object CollegeRouteFinder {

    private data class Vertex(val x: Float, val y: Float)

    data class Route(val points: List<MapPoint>, val distance: Float)

    /**
     * Маршрут через несколько этажей. [floorPaths] — путь на каждом этаже по порядку
     * (для отрисовки: приложение показывает этаж за этажом). [distance] — суммарная длина.
     */
    data class FloorPath(val floorId: String, val points: List<MapPoint>)

    data class MultiFloorRoute(val floorPaths: List<FloorPath>, val distance: Float) {
        val points: List<MapPoint> get() = floorPaths.flatMap { it.points }
        val crossesFloors: Boolean get() = floorPaths.size > 1
    }

    private const val JOIN_RADIUS = 0.05f

    /** Лестницы одного ствола на соседних этажах стоят почти в одной точке (замер: 0.007–0.041). */
    private const val STAIR_PAIR_RADIUS = 0.06f

    /**
     * Граф этажа: вершины = узлы(nodes) + точки коридоров; рёбра = links узлов, соседние точки
     * коридоров, и концы коридоров, примыкающие к узлам в радиусе JOIN_RADIUS.
     */
    private fun buildGraph(floor: FloorMap): Pair<List<Vertex>, List<List<Pair<Int, Float>>>> {
        val vertices = mutableListOf<Vertex>()
        val adjacency = mutableListOf<MutableList<Pair<Int, Float>>>()

        fun addVertex(x: Float, y: Float): Int {
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

        val nodeIndex = HashMap<String, Int>()
        floor.nodes.forEach { n -> nodeIndex[n.id] = addVertex(n.x, n.y) }
        floor.nodes.forEach { n ->
            val a = nodeIndex[n.id] ?: return@forEach
            n.links.forEach { other -> nodeIndex[other]?.let { link(a, it) } }
        }

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
        return vertices to adjacency
    }

    /** Путь по графу одного этажа от точки (ax,ay) до (bx,by): последовательность точек графа (без концов). */
    private fun floorPath(
        floor: FloorMap, ax: Float, ay: Float, bx: Float, by: Float
    ): List<MapPoint>? {
        val (vertices, adjacency) = buildGraph(floor)
        if (vertices.isEmpty()) return null
        val start = nearestVertex(vertices, ax, ay)
        val finish = nearestVertex(vertices, bx, by)
        if (start < 0 || finish < 0) return null
        val path = shortestPath(vertices, adjacency, start, finish) ?: return null
        return path.map { MapPoint(vertices[it].x, vertices[it].y) }
    }

    fun findRoute(
        from: MapRoom,
        to: MapRoom,
        floor: FloorMap
    ): Route? {
        if (from.id == to.id) return null
        val graph = floorPath(floor, from.x, from.y, to.x, to.y) ?: return null
        val points = mutableListOf(MapPoint(from.x, from.y))
        points += graph
        points.add(MapPoint(to.x, to.y))
        var total = 0f
        for (i in 1 until graph.size) total += distP(graph[i - 1], graph[i])
        return Route(points, total)
    }

    /**
     * Мультиэтажный маршрут. Если кабинеты на одном этаже — обычный [findRoute]. Иначе идёт по
     * цепочке этажей (порядок из [CollegeMap.Floor]) и на каждой границе выбирает ОБЩУЮ лестницу
     * (пригодную по [isUsableStair]) на обоих этажах, стоящую рядом (STAIR_PAIR_RADIUS), ближайшую
     * к текущей позиции — greedy, глобально-оптимальный перебор лестниц не нужен на реальном плане.
     *
     * @param isUsableStair фильтр пригодных лестниц (заказчик: аварийными не пользуемся). По умолчанию все.
     */
    fun findMultiFloorRoute(
        from: MapRoom,
        to: MapRoom,
        floors: Map<String, FloorMap>,
        isUsableStair: (MapEntrance) -> Boolean = { true }
    ): MultiFloorRoute? {
        val fromId = CollegeMap.floorForRoom(from.number)?.id ?: return null
        val toId = CollegeMap.floorForRoom(to.number)?.id ?: return null
        val fromFloor = floors[fromId] ?: return null
        if (fromId == toId) {
            val r = findRoute(from, to, fromFloor) ?: return null
            return MultiFloorRoute(listOf(FloorPath(fromId, r.points)), r.distance)
        }

        val order = CollegeMap.Floor.entries.map { it.id }
        val fi = order.indexOf(fromId)
        val ti = order.indexOf(toId)
        if (fi < 0 || ti < 0) return null
        val chain = if (fi < ti) order.subList(fi, ti + 1) else order.subList(ti, fi + 1).reversed()

        fun stairs(fid: String) = floors[fid]?.entrances
            ?.filter { it.kind == "stairs" && isUsableStair(it) } ?: emptyList()

        val result = mutableListOf<FloorPath>()
        var total = 0f
        // Текущая точка на текущем этаже: старт — центр кабинета from
        var cx = from.x; var cy = from.y
        for (i in 0 until chain.size) {
            val fid = chain[i]
            val floor = floors[fid] ?: return null
            if (i == chain.size - 1) {
                // Последний этаж: путь от текущей точки до кабинета to
                val seg = floorPath(floor, cx, cy, to.x, to.y) ?: return null
                val pts = mutableListOf(MapPoint(cx, cy)) + seg + MapPoint(to.x, to.y)
                result.add(FloorPath(fid, pts))
                for (k in 1 until seg.size) total += distP(seg[k - 1], seg[k])
                break
            }
            // Ищем общую лестницу: на этом этаже и на следующем, стоящую рядом
            val nextId = chain[i + 1]
            val here = stairs(fid)
            val there = stairs(nextId)
            var bestHere: MapEntrance? = null
            var bestThere: MapEntrance? = null
            var bestCost = Float.MAX_VALUE
            for (a in here) for (b in there) {
                if (distE(a, b) > STAIR_PAIR_RADIUS) continue
                val cost = hypot(cx - a.x, cy - a.y)
                if (cost < bestCost) { bestCost = cost; bestHere = a; bestThere = b }
            }
            val stairA = bestHere ?: return null
            val stairB = bestThere ?: return null
            val seg = floorPath(floor, cx, cy, stairA.x, stairA.y) ?: return null
            val pts = mutableListOf(MapPoint(cx, cy)) + seg + MapPoint(stairA.x, stairA.y)
            result.add(FloorPath(fid, pts))
            for (k in 1 until seg.size) total += distP(seg[k - 1], seg[k])
            // Переход на следующий этаж: продолжаем от сопряжённой лестницы
            cx = stairB.x; cy = stairB.y
        }
        return MultiFloorRoute(result, total)
    }

    private fun hypot(dx: Float, dy: Float) = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
    private fun distE(a: MapEntrance, b: MapEntrance) = hypot(a.x - b.x, a.y - b.y)
    private fun distP(a: MapPoint, b: MapPoint) = hypot(a.x - b.x, a.y - b.y)

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
