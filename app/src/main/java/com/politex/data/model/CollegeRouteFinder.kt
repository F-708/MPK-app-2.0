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
     *
     * W40a: [attach] — точки старта/финиша (дверь комнаты ?: центр). Каждая ПРОЕЦИРУЕТСЯ
     * на ближайший сегмент коридора этажа (перпендикуляр без ограничения радиуса) и
     * подключается к проекционной точке, связанной с концами сегмента — маршрут выходит
     * из двери ПРЯМО в коридор, без крюка к дальнему перекрёстку (ближайшая ВЕРШИНА может
     * быть дальним узлом, хотя коридор проходит мимо двери). Фолбэк: если проекция ДАЛЬШЕ
     * ближайшей вершины (комната в крыле без разметки рядом) — подключение к вершине,
     * как раньше. Возврат: (вершины, рёбра, индексы attach-вершин).
     */
    private fun buildGraph(
        floor: FloorMap,
        attach: List<Pair<Float, Float>> = emptyList(),
    ): Triple<List<Vertex>, List<List<Pair<Int, Float>>>, List<Int>> {
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
        // W40b: проекции, лежащие на ОДНОМ сегменте, дополнительно записываются для цепочки.
        val onSegment = HashMap<Int, MutableList<Pair<Float, Int>>>() // сегмент → (t, вершина)
        val snapshot = vertices.toList()
        for (v in snapshot) {
            for ((si, ab) in segments.withIndex()) {
                val (a, b) = ab
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
                onSegment.getOrPut(si) { mutableListOf() }.add(tOnSegment(q, a, b) to qv)
            }
        }

        // W40a: подключение attach-точек (старт/финиш) ПРОЕКЦИЕЙ на ближайший сегмент
        // коридора этажа. Перебираются все сегменты, выбирается минимальный перпендикуляр;
        // проекция связывается с концами сегмента (механизм как в склейке выше). Если
        // проекция ДАЛЬШЕ ближайшей вершины графа (комната в крыле без коридора рядом) —
        // фолбэк на ближайшую вершину, как до W40a. Фолбэк ищется только среди «настоящих»
        // вершин (до attach): иначе на пустом этаже две комнаты связались бы напрямую.
        val n0 = vertices.size
        val attachIdx = attach.map { (ax, ay) ->
            val rv = addVertex(ax, ay)
            val p = Vertex(ax, ay)
            var bestQ: Vertex? = null
            var bestD = Float.MAX_VALUE
            var bestSi = -1
            for ((si, ab) in segments.withIndex()) {
                val (a, b) = ab
                val q = projectPointOnSegment(p, a, b) ?: continue
                val d = dist(p, q)
                if (d < bestD) {
                    bestD = d
                    bestQ = q
                    bestSi = si
                }
            }
            val nv = nearestVertex(vertices.subList(0, n0), ax, ay)
            val nvD = if (nv >= 0) dist(p, vertices[nv]) else Float.MAX_VALUE
            if (bestQ != null && bestD <= nvD) {
                val qi = addVertex(bestQ.x, bestQ.y)
                link(rv, qi)
                val (a, b) = segments[bestSi]
                val ai = vertices.indexOf(a)
                val bi = vertices.indexOf(b)
                if (ai >= 0) link(qi, ai)
                if (bi >= 0) link(qi, bi)
                onSegment.getOrPut(bestSi) { mutableListOf() }.add(tOnSegment(bestQ, a, b) to qi)
            } else if (nv >= 0) {
                link(rv, nv)
            }
            rv
        }

        // W40b: проекции одного сегмента — ЦЕПОЧКОЙ по t (a—q1—q2—…—b). Коридоры заказчика
        // размечены «на глаз» длинными сегментами: без цепочки две двери на одном сегменте
        // связаны только через его дальние концы — маршрут делает крюк к перекрёстку
        // (жалоба «из 107 в 123 идёт почти до 115 и обратно»). Цепочка даёт рёбра вдоль
        // коридора между соседними проекциями.
        onSegment.forEach { (si, list) ->
            val (a, b) = segments[si]
            val chain = mutableListOf(vertices.indexOf(a))
            list.sortedBy { it.first }.forEach { e -> if (e.second != chain.last()) chain.add(e.second) }
            val bi = vertices.indexOf(b)
            if (bi != chain.last()) chain.add(bi)
            for (i in 1 until chain.size) link(chain[i - 1], chain[i])
        }
        return Triple(vertices, adjacency, attachIdx)
    }

    /** Параметр t точки q, лежащей на отрезке ab (0 = a, 1 = b). */
    private fun tOnSegment(q: Vertex, a: Vertex, b: Vertex): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        if (len2 == 0f) return 0f
        return ((q.x - a.x) * dx + (q.y - a.y) * dy) / len2
    }

    /** Проекция точки p на отрезок ab (t зажат в [0,1]); без ограничения радиуса. */
    private fun projectPointOnSegment(p: Vertex, a: Vertex, b: Vertex): Vertex? {
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
        return Vertex(a.x + tc * dx, a.y + tc * dy)
    }

    /** Проекция точки p на отрезок ab; null если перпендикуляр длиннее PROJECTION_RADIUS. */
    private fun projectOnSegment(p: Vertex, a: Vertex, b: Vertex): Vertex? =
        projectPointOnSegment(p, a, b)?.takeIf { dist(p, it) <= PROJECTION_RADIUS }

    /** Путь по графу одного этажа от точки (ax,ay) до (bx,by): последовательность точек графа (без концов). */
    private fun floorPath(
        floor: FloorMap,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
    ): List<MapPoint>? {
        val (vertices, adjacency, attachIdx) = buildGraph(floor, listOf(ax to ay, bx to by))
        if (attachIdx.size < 2) return null
        val path = shortestPath(vertices, adjacency, attachIdx[0], attachIdx[1]) ?: return null
        val out = mutableListOf<MapPoint>()
        path.forEach {
            val p = MapPoint(vertices[it].x, vertices[it].y)
            if (out.isEmpty() || distP(out.last(), p) > 1e-4f) out.add(p) // без дублей точек
        }
        return out
    }

    fun findRoute(
        from: MapRoom,
        to: MapRoom,
        floor: FloorMap,
    ): Route? {
        if (from.id == to.id) return null
        // W40a: старт/финиш — от двери (если размечена), центр — для отрисовки
        val fromP = from.door ?: MapPoint(from.x, from.y)
        val toP = to.door ?: MapPoint(to.x, to.y)
        val graph = floorPath(floor, fromP.x, fromP.y, toP.x, toP.y) ?: return null
        val raw = mutableListOf(MapPoint(from.x, from.y)) + graph + MapPoint(to.x, to.y)
        val points = mutableListOf<MapPoint>()
        raw.forEach { if (points.isEmpty() || distP(points.last(), it) > 1e-4f) points.add(it) }
        var total = 0f
        for (i in 1 until points.size) total += distP(points[i - 1], points[i])
        return Route(points, total)
    }

    /** W40a: точка подключения комнаты к графу — дверь (приоритет разметки) или центр. */
    private fun attachPoint(r: MapRoom): Pair<Float, Float> {
        val p = r.door ?: MapPoint(r.x, r.y)
        return p.x to p.y
    }

    /**
     * Подъём лестницы — фиксированная «стоимость» межэтажного перехода. Реальные координаты
     * сопряжённых лестниц почти совпадают (0.007–0.041), поэтому без стоимости алгоритм мог бы
     * «дёргаться» между этажами; штраф делает переход осмысленным, но не запрещает его.
     */
    private const val STAIR_COST = 0.25f

    /**
     * Мультиэтажный маршрут: ОДИН глобальный граф по ВСЕМ этажам + межэтажные рёбра
     * лестниц, один Дейкстра A→B (W39b). Раньше строилась цепочка этажей «от from к to» —
     * на плане «3-4» лежат РАЗНЫЕ корпуса (301–330 корпус 1, 331–348 корпус 2, переход только
     * через 2 этаж): прямой путь по плану ложен, а «цепочка этажей» не умеет возвращаться
     * на тот же план через другой корпус. Глобальный граф решает это сам: Дейкстра идёт
     * вниз по лестнице → 2 этаж → другой корпус → вверх (331→301), а 4-е этажи (401–405)
     * достигаются только через связанную с ними лестницу.
     *
     * Лестницы: только [isUsableStair]. Сопряжение между этажами (W39a):
     * 1) ЯВНЫЕ entrance.links — пары по id (links заданы вручную заказчиком; могут указывать
     *    на любой этаж, в т.ч. на ту же floorId — план «3-4» несёт и 3-й, и 4-й этажи);
     * 2) фолбэк по близости координат (≤ STAIR_PAIR_RADIUS) — только для лестниц БЕЗ links
     *    на соседних этажах (обратная совместимость старых карт).
     *
     * [floorPaths] — сегменты по этажам (для отрисовки); граница сегмента = смена этажа
     * ИЛИ переход по лестнице (на плане «3-4» это даёт два сегмента с одной floorId —
     * подход к лестнице у 335 и площадка 4-го этажа).
     *
     * @param isUsableStair фильтр пригодных лестниц (заказчик: аварийными не пользуемся).
     */
    fun findMultiFloorRoute(
        from: MapRoom,
        to: MapRoom,
        floors: Map<String, FloorMap>,
        isUsableStair: (MapEntrance) -> Boolean = { it.usable },
    ): MultiFloorRoute? {
        if (from.id == to.id) return null
        val fromId = CollegeMap.floorForRoom(from.number)?.id ?: return null
        val toId = CollegeMap.floorForRoom(to.number)?.id ?: return null
        val order = CollegeMap.Floor.entries.map { it.id }.filter { floors.containsKey(it) }
        if (fromId !in order || toId !in order) return null

        // --- Единый глобальный граф: вершины помечены этажом ---
        val gVerts = mutableListOf<Triple<String, Float, Float>>() // (floorId, x, y)
        val gAdj = mutableListOf<MutableList<Pair<Int, Float>>>()
        val stairEdges = HashSet<Long>() // упакованная пара вершин — переходы лестниц
        fun edgeKey(a: Int, b: Int): Long = if (a < b) a * 1_000_000L + b else b * 1_000_000L + a

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
        fun addStairEdge(a: Int, b: Int) {
            if (a == b || !stairEdges.add(edgeKey(a, b))) return // links симметричны — не дублируем
            linkV(a, b, STAIR_COST)
        }

        // Этажные подграфы (nodes + corridors, как в buildGraph) и индексы лестниц.
        // Комнаты старта/финиша подключаются как attach-точки buildGraph (W40a: дверь?:
        // центр → ПРОЕКЦИЯ на ближайший сегмент коридора, фолбэк — ближайшая вершина).
        val stairVById = HashMap<String, Int>()
        val stairs = mutableListOf<Triple<String, MapEntrance, Int>>() // (floorId, entrance, vertex)
        var startV = -1
        var finishV = -1
        order.forEach { fid ->
            val floor = floors.getValue(fid)
            val attach = ArrayList<Pair<Float, Float>>()
            if (fid == fromId) attach.add(attachPoint(from))
            if (fid == toId) attach.add(attachPoint(to))
            val (verts, adj, attachIdx) = buildGraph(floor, attach)
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
                stairVById[e.id] = sv
                stairs.add(Triple(fid, e, sv))
            }
            // Комнаты старта/финиша уже в графе этажа как attach-вершины (индексы совпадают
            // после слияния: локальный i → глобальный base + i)
            attachIdx.forEachIndexed { k, idx ->
                if (fid == fromId && (fid != toId || k == 0)) startV = base + idx else finishV = base + idx
            }
        }

        // Межэтажные рёбра лестниц. 1) явные links (id → вершина на любом этаже);
        // 2) фолбэк близости — только пары лестниц БЕЗ links на соседних этажах.
        stairs.filter { it.second.links.isNotEmpty() }.forEach { (_, e, sv) ->
            e.links.forEach { tid ->
                val tv = stairVById[tid] ?: return@forEach
                addStairEdge(sv, tv)
            }
        }
        for (i in 0 until order.size - 1) {
            val here = stairs.filter { it.first == order[i] && it.second.links.isEmpty() }
            val there = stairs.filter { it.first == order[i + 1] && it.second.links.isEmpty() }
            for (a in here) {
                for (b in there) {
                    if (distE(a.second, b.second) <= STAIR_PAIR_RADIUS) addStairEdge(a.third, b.third)
                }
            }
        }
        if (gVerts.isEmpty() || startV < 0 || finishV < 0) return null

        val path = shortestPathGlobal(gAdj, gVerts.size, startV, finishV) ?: return null

        // Разбиение пути на сегменты: граница = смена этажа ИЛИ ребро лестницы
        val result = mutableListOf<FloorPath>()
        var curFloor = gVerts[path.first()].first
        var cur = mutableListOf<MapPoint>()
        fun flush() {
            if (cur.size >= 1) result.add(FloorPath(curFloor, cur.toList()))
            cur = mutableListOf()
        }
        for (i in path.indices) {
            val v = gVerts[path[i]]
            val stairHop = i > 0 && stairEdges.contains(edgeKey(path[i - 1], path[i]))
            if (v.first != curFloor || stairHop) {
                flush()
                curFloor = v.first
            }
            cur.add(MapPoint(v.second, v.third))
        }
        flush()
        // Начало/конец — центры кабинетов (путь стартует от двери — W40a — центр только
        // для отрисовки; без двери attach-точка и есть центр)
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

    /**
     * W39c: ортогонализация маршрута для отрисовки. Диагональный сегмент P1→P2
     * заменяется L-уголком (x1,y2) либо (x2,y1); выбирается уголок, чьи отрезки НЕ
     * пересекают прямоугольники комнат (status != nonexistent; комнаты концов сегмента
     * не считаются — маршрут из них начинается/в них заканчивается). Оба вариантa
     * пересекают комнаты → остаётся диагональ (меньшее зло). Уже-ортогональные сегменты
     * не трогаются; при равном выборе продолжается ось предыдущего сегмента (меньше зигзагов).
     * ponytail: полная избегатель-геометрия (A* с штрафами за проход комнат) — потолок,
     * добавить, если L-уголков окажется недостаточно.
     */
    fun orthogonalize(points: List<MapPoint>, rooms: List<MapRoom>): List<MapPoint> {
        if (points.size < 2) return points
        val rects = rooms.asSequence()
            .filter { it.status != "nonexistent" && it.w > 0f && it.h > 0f }
            .map { floatArrayOf(it.x - it.w / 2f, it.y - it.h / 2f, it.x + it.w / 2f, it.y + it.h / 2f) }
            .toList()

        fun contains(r: FloatArray, p: MapPoint): Boolean =
            p.x >= r[0] && p.x <= r[2] && p.y >= r[1] && p.y <= r[3]

        // Пересечение ОСЕВОГО отрезка (гор./верт.) с прямоугольником; касание стен не считается
        fun crosses(a: MapPoint, b: MapPoint, r: FloatArray): Boolean {
            val minY = Math.min(a.y, b.y)
            val maxY = Math.max(a.y, b.y)
            val minX = Math.min(a.x, b.x)
            val maxX = Math.max(a.x, b.x)
            return if (Math.abs(a.y - b.y) < ORTHO_EPS) {
                a.y > r[1] && a.y < r[3] && maxX > r[0] && minX < r[2]
            } else {
                a.x > r[0] && a.x < r[2] && maxY > r[1] && minY < r[3]
            }
        }
        fun blocked(a: MapPoint, b: MapPoint, rs: List<FloatArray>): Boolean = rs.any { crosses(a, b, it) }

        val out = mutableListOf(points.first())
        var lastLegHorizontal = false // ось последнего сегмента (для выбора уголка)
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val dx = Math.abs(b.x - a.x)
            val dy = Math.abs(b.y - a.y)
            if (dx < ORTHO_EPS || dy < ORTHO_EPS) {
                out.add(b)
                lastLegHorizontal = dy < ORTHO_EPS
                continue
            }
            val active = rects.filter { !contains(it, a) && !contains(it, b) }
            val c1 = MapPoint(a.x, b.y) // сначала вертикаль, потом горизонталь
            val c2 = MapPoint(b.x, a.y) // сначала горизонталь, потом вертикаль
            val c1Free = !blocked(a, c1, active) && !blocked(c1, b, active)
            val c2Free = !blocked(a, c2, active) && !blocked(c2, b, active)
            val corner = when {
                c1Free && c2Free -> if (lastLegHorizontal) c2 else c1
                c1Free -> c1
                c2Free -> c2
                else -> null
            }
            if (corner == null) {
                out.add(b)
                lastLegHorizontal = false
            } else {
                out.add(corner)
                out.add(b)
                lastLegHorizontal = corner === c1 // у c1 последняя нога — горизонталь
            }
        }
        return out
    }

    private const val ORTHO_EPS = 1e-4f

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
