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
     * Радиус «проекции на коридор»: вершина (узел, конец коридора, лестница, комната) соединяется
     * с ближайшей точкой ЛЮБОГО сегмента ЛЮБОГО коридора, если перпендикуляр короче этого радиуса.
     * Коридоры на плане пересекаются крест-накрест без общих точек (разметка рисовалась вручную),
     * без проекции граф распадался на 2-5 компонент на этаж. Замер по реальным данным: 0.05
     * связывает 2 этаж полностью, 1 и 3-4 — до 2 компонент.
     */
    private const val PROJECTION_RADIUS = 0.05f

    /**
     * Граф этажа: вершины = узлы(nodes) + точки коридоров + проекции; рёбра = links узлов,
     * соседние точки коридоров, концы коридоров к узлам в радиусе JOIN_RADIUS, и ПРОЕКЦИИ
     * вершин на сегменты коридоров в радиусе PROJECTION_RADIUS (склейка пересечений).
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

        val segments = mutableListOf<Pair<Vertex, Vertex>>()
        floor.corridors.forEach { c ->
            if (c.points.size < 2) return@forEach
            val idx = c.points.map { p -> addVertex(p.x, p.y) }
            for (i in 1 until idx.size) {
                link(idx[i - 1], idx[i])
                segments.add(vertices[idx[i - 1]] to vertices[idx[i]])
            }
            listOf(idx.first(), idx.last()).forEach { end ->
                var best = -1
                var bestD = JOIN_RADIUS
                nodeIndex.values.forEach { ni ->
                    val d = dist(vertices[end], vertices[ni])
                    if (d < bestD) {
                        bestD = d
                        best = ni
                    }
                }
                if (best >= 0) link(end, best)
            }
        }

        // Проекционная склейка: каждая существующая вершина соединяется с ближайшей точкой
        // каждого сегмента, если перпендикуляр ≤ PROJECTION_RADIUS. Снимок списка вершин
        // фиксируется до цикла — добавленные проекции сами не проецируются (иначе O(n^3)).
        val snapshot = vertices.toList()
        for (v in snapshot) {
            for ((a, b) in segments) {
                val q = projectOnSegment(v, a, b) ?: continue
                if (dist(v, q) < 1e-4f) continue // уже на сегменте
                val qv = addVertex(q.x, q.y)
                link(vertices.indexOfFirst { it === v }, qv)
                // проекция обязана лежать НА сегменте: addVertex её задедупит с точкой
                // коридора только при точном совпадении; иначе соединим с концами сегмента
                val ai = vertices.indexOf(a)
                val bi = vertices.indexOf(b)
                if (ai >= 0 && vertices[ai] !== q) link(qv, ai)
                if (bi >= 0 && vertices[bi] !== q) link(qv, bi)
            }
        }
        return vertices to adjacency
    }

    /** Проекция точки p на отрезок ab; null если перпендикуляр длиннее PROJECTION_RADIUS. */
    private fun projectOnSegment(p: Vertex, a: Vertex, b: Vertex): Vertex? {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        if (len2 == 0f) return null
        val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2
        val tc = if (t < 0f) {
            0f
        } else if (t > 1f) {
            1f
        } else {
            t
        }
        val q = Vertex(a.x + tc * dx, a.y + tc * dy)
        return if (dist(p, q) <= PROJECTION_RADIUS) q else null
    }

    /** Путь по графу одного этажа от точки (ax,ay) до (bx,by): последовательность точек графа (без концов). */
    private fun floorPath(
        floor: FloorMap,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
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
        floor: FloorMap,
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
     * Подъём лестницы — фиксированная «стоимость» межэтажного перехода. Реальные координаты
     * сопряжённых лестниц почти совпадают (0.007–0.041), поэтому без стоимости алгоритм мог бы
     * «дёргаться» между этажами; штраф делает переход осмысленным, но не запрещает его.
     */
    private const val STAIR_COST = 0.25f

    /**
     * Мультиэтажный маршрут: ЕДИНЫЙ граф по всем этажам цепочки + межэтажные рёбра лестниц,
     * один Дейкстра. Greedy-выбор лестницы на каждом этаже (первая версия) ломался на
     * фрагментированном графе: комната могла быть связна с другой лестницей, не выбранной.
     * Глобальный поиск выбирает лестницы автоматически по кратчайшей стоимости.
     *
     * Лестницы: только [isUsableStair] (по умолчанию поле usable — список заказчика);
     * сопряжение между соседними этажами — по близости координат (≤ STAIR_PAIR_RADIUS).
     *
     * @param isUsableStair фильтр пригодных лестниц (заказчик: аварийными не пользуемся).
     */
    fun findMultiFloorRoute(
        from: MapRoom,
        to: MapRoom,
        floors: Map<String, FloorMap>,
        isUsableStair: (MapEntrance) -> Boolean = { it.usable },
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

        // --- Единый граф: вершины помечены этажом ---
        val gVerts = mutableListOf<Triple<String, Float, Float>>() // (floorId, x, y)
        val gAdj = mutableListOf<MutableList<Pair<Int, Float>>>()
        fun addV(fid: String, x: Float, y: Float): Int {
            gVerts.forEachIndexed { i, v ->
                if (v.first == fid && Math.abs(v.second - x) < 1e-4f && Math.abs(v.third - y) < 1e-4f) return i
            }
            gVerts.add(Triple(fid, x, y))
            gAdj.add(mutableListOf())
            return gVerts.size - 1
        }
        fun linkV(a: Int, b: Int, w: Float? = null) {
            if (a == b) return
            val d = w ?: hypot(gVerts[a].second - gVerts[b].second, gVerts[a].third - gVerts[b].third)
            gAdj[a].add(b to d)
            gAdj[b].add(a to d)
        }

        // Этажные подграфы (nodes + corridors, как в buildGraph) и индексы лестниц
        val stairByFloor = mutableMapOf<Int, MutableList<Int>>()
        var startV = -1
        var finishV = -1
        chain.forEachIndexed { pos, fid ->
            val floor = floors[fid] ?: return null
            val (verts, adj) = buildGraph(floor)
            val base = gVerts.size
            verts.forEach { (x, y) -> addV(fid, x, y) }
            adj.forEachIndexed { i, edges -> edges.forEach { (j, d) -> if (j > i) linkV(base + i, base + j, d) } }

            fun snap(x: Float, y: Float): Int {
                // Ближайшая вершина этажного графа к точке (лестница/комната)
                var best = -1
                var bd = Float.MAX_VALUE
                verts.forEachIndexed { i, v ->
                    val d = (v.x - x) * (v.x - x) + (v.y - y) * (v.y - y)
                    if (d < bd) {
                        bd = d
                        best = i
                    }
                }
                return if (best < 0) -1 else base + best
            }

            // Лестницы этажа → вершины графа (ребро до ближайшей вершины коридора/узла)
            floor.entrances.filter { it.kind == "stairs" && isUsableStair(it) }.forEach { e ->
                val sv = addV(fid, e.x, e.y)
                val near = snap(e.x, e.y)
                if (near >= 0) linkV(sv, near)
                stairByFloor.getOrPut(pos) { mutableListOf() }.add(sv)
            }
            // Комнаты старта/финиша на своих этажах — тоже вершины, связанные с графом этажа.
            // ponytail: привязка к БЛИЖАЙШЕЙ вершине без ограничения радиуса — для дальних комнат
            // (~0.3 от коридора, wing'ы без vision-доразметки) линия подхода будет диагональной;
            // когда vision-разметка f1/f3-4 добавит коридоры, диагонали исчезнут сами.
            if (fid == fromId) {
                val v = addV(fid, from.x, from.y)
                val n = snap(from.x, from.y)
                if (n >= 0) linkV(v, n)
                startV = v
            }
            if (fid == toId) {
                val v = addV(fid, to.x, to.y)
                val n = snap(to.x, to.y)
                if (n >= 0) linkV(v, n)
                finishV = v
            }
        }

        // Межэтажные рёбра: сопряжённые лестницы соседних этажей (≤ STAIR_PAIR_RADIUS)
        for (pos in 0 until chain.size - 1) {
            val here = stairByFloor[pos] ?: continue
            val there = stairByFloor[pos + 1] ?: continue
            for (a in here) {
                for (b in there) {
                    val va = gVerts[a]
                    val vb = gVerts[b]
                    if (hypot(va.second - vb.second, va.third - vb.third) <= STAIR_PAIR_RADIUS) {
                        linkV(a, b, STAIR_COST)
                    }
                }
            }
        }
        if (gVerts.isEmpty()) return null

        val path = shortestPathGlobal(gAdj, gVerts.size, startV, finishV) ?: return null

        // Разбиение пути на сегменты по этажам (для послойной отрисовки)
        val result = mutableListOf<FloorPath>()
        var curFloor = gVerts[path.first()].first
        var cur = mutableListOf<MapPoint>()
        fun flush() {
            if (cur.size >= 1) result.add(FloorPath(curFloor, cur.toList()))
            cur = mutableListOf()
        }
        for (vi in path) {
            val v = gVerts[vi]
            if (v.first != curFloor) {
                flush()
                curFloor = v.first
            }
            cur.add(MapPoint(v.second, v.third))
        }
        flush()
        // Начало/конец — центры кабинетов (могут отличаться от snapped-вершин)
        if (result.isNotEmpty()) {
            val first = result[0]
            if (first.points.isEmpty() || distP(first.points.first(), MapPoint(from.x, from.y)) > 1e-4f) {
                result[0] = first.copy(points = listOf(MapPoint(from.x, from.y)) + first.points)
            }
            val last = result[result.size - 1]
            if (last.points.isEmpty() || distP(last.points.last(), MapPoint(to.x, to.y)) > 1e-4f) {
                result[result.size - 1] = last.copy(points = last.points + MapPoint(to.x, to.y))
            }
        }
        var total = 0f
        result.forEach { fp -> for (i in 1 until fp.points.size) total += distP(fp.points[i - 1], fp.points[i]) }
        total += STAIR_COST * (result.size - 1) // межэтажные переходы учтены в стоимости, не в геометрии
        return MultiFloorRoute(result, total)
    }

    private fun shortestPathGlobal(
        adjacency: List<List<Pair<Int, Float>>>,
        n: Int,
        start: Int,
        finish: Int,
    ): List<Int>? {
        if (start < 0 || finish < 0 || start >= n || finish >= n) return null
        val best = FloatArray(n) { Float.MAX_VALUE }
        val prev = IntArray(n) { -1 }
        val used = BooleanArray(n)
        best[start] = 0f
        repeat(n) {
            var v = -1
            var bestD = Float.MAX_VALUE
            for (i in 0 until n) {
                if (!used[i] && best[i] < bestD) {
                    bestD = best[i]
                    v = i
                }
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

    private fun hypot(dx: Float, dy: Float) = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
    private fun distE(a: MapEntrance, b: MapEntrance) = hypot(a.x - b.x, a.y - b.y)
    private fun distP(a: MapPoint, b: MapPoint) = hypot(a.x - b.x, a.y - b.y)

    private fun nearestVertex(vertices: List<Vertex>, x: Float, y: Float): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        vertices.forEachIndexed { i, v ->
            val d = (v.x - x) * (v.x - x) + (v.y - y) * (v.y - y)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    private fun dist(a: Vertex, b: Vertex): Float = Math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    private fun shortestPath(
        vertices: List<Vertex>,
        adjacency: List<List<Pair<Int, Float>>>,
        start: Int,
        finish: Int,
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
                if (!used[i] && best[i] < bestD) {
                    bestD = best[i]
                    v = i
                }
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
