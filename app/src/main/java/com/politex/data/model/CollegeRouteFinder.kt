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
     * Y7: проекционное ребро, пробивающее комнату, НЕ создаётся (диагональные склейки
     * впритирку к углам кабинетов резали их углы — жалоба «маршрут сквозь кабинет»).
     *
     * W40a/Y7: [attach] — ЦЕПОЧКИ кандидатов старта/финиша на комнату: дверь ?:
     * (ближайшая точка контура к коридору, центр). Подключение — ортогональный Г-подход:
     * проекция q на сегмент коридора + уголок c (сначала по оси двери до оси коридора,
     * потом вдоль); обе ноги не должны пересекать чужие комнаты (segCrossesRect), точка
     * подключения скользит вдоль коридора (кандидаты q по возрастанию расстояния), пока
     * Г не станет чистым. Нет чистого Г — прямая чистая проекция (X26), нет и её —
     * ближайшая вершина. Возврат: (вершины, рёбра, индексы attach-вершин).
     */
    private fun buildGraph(
        floor: FloorMap,
        attach: List<List<Pair<Float, Float>>> = emptyList(),
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

        // Y7: rect'ы кабинетов — для проверок «нога не пробивает чужую комнату» (склейка и attach)
        val wallRects = floor.rooms
            .filter { it.status != "nonexistent" && it.status != "passage" && it.w > 0f && it.h > 0f }
            .map { floatArrayOf(it.x - it.w / 2f, it.y - it.h / 2f, it.x + it.w / 2f, it.y + it.h / 2f) }

        // Проекционная склейка: каждая существующая вершина соединяется с ближайшей точкой
        // каждого сегмента, если перпендикуляр ≤ PROJECTION_RADIUS. Снимок списка вершин
        // фиксируется до цикла — добавленные проекции сами не проецируются (иначе O(n^3)).
        // W40b: проекции, лежащие на ОДНОМ сегменте, дополнительно записываются для цепочки.
        // Y7: склейка, пробивающая комнату (диагональ впритирку к углу кабинета), не создаётся.
        val onSegment = HashMap<Int, MutableList<Pair<Float, Int>>>() // сегмент → (t, вершина)
        val snapshot = vertices.toList()
        for (v in snapshot) {
            for ((si, ab) in segments.withIndex()) {
                val (a, b) = ab
                val q = projectOnSegment(v, a, b) ?: continue
                if (dist(v, q) < 1e-4f) continue // уже на сегменте
                if (wallRects.any { r ->
                        !rectContains(r, v.x, v.y) && !rectContains(r, q.x, q.y) &&
                            segCrossesRect(v.x, v.y, q.x, q.y, r)
                    }
                ) {
                    continue
                }
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

        // W40a/X26/Y7: подключение attach-цепочек (дверь ?: контур ?: центр).
        // 1) Y7: ортогональный Г-подход: проекция q на сегмент коридора + уголок c — обе ноги
        //    (точка→c, c→q) не пробивают чужие комнаты; q скользит вдоль коридора по возрастанию
        //    расстояния, пока Г не станет чистым; кандидаты точки — по цепочке.
        // 2) X26: нет чистого Г — прямая чистая проекция первой точки цепочки.
        // 3) Фолбэк — ближайшая «настоящая» вершина (до attach), как раньше.
        val n0 = vertices.size
        val attachIdx = attach.map { chain ->
            val first = chain.first()

            fun legClean(
                ax: Float,
                ay: Float,
                x0: Float,
                y0: Float,
                x1: Float,
                y1: Float,
            ): Boolean = wallRects.none { r -> !rectContains(r, ax, ay) && segCrossesRect(x0, y0, x1, y1, r) }

            fun projections(ax: Float, ay: Float): List<Triple<Vertex, Int, Float>> {
                val p = Vertex(ax, ay)
                val out = ArrayList<Triple<Vertex, Int, Float>>()
                for ((si, ab) in segments.withIndex()) {
                    val q = projectPointOnSegment(p, ab.first, ab.second) ?: continue
                    out.add(Triple(q, si, dist(p, q)))
                }
                out.sortBy { it.third }
                return out
            }

            fun wireCorner(
                ax: Float,
                ay: Float,
                corner: Vertex,
                q: Vertex,
                si: Int,
            ): Int {
                val rv = addVertex(ax, ay)
                val ci = addVertex(corner.x, corner.y)
                if (ci != rv) link(rv, ci)
                val qi = addVertex(q.x, q.y)
                if (qi != ci) link(ci, qi)
                val (a, b) = segments[si]
                val ai = vertices.indexOf(a)
                val bi = vertices.indexOf(b)
                if (ai >= 0) link(qi, ai)
                if (bi >= 0) link(qi, bi)
                onSegment.getOrPut(si) { mutableListOf() }.add(tOnSegment(q, a, b) to qi)
                return rv
            }

            var rv = -1
            for ((ax, ay) in chain) {
                val p = Vertex(ax, ay)
                val hit =
                    projections(ax, ay).firstNotNullOfOrNull { (q, si, _) ->
                        val corner =
                            listOf(Vertex(ax, q.y), Vertex(q.x, ay)).firstOrNull { c ->
                                (dist(p, c) < 1e-4f || legClean(ax, ay, p.x, p.y, c.x, c.y)) &&
                                    (dist(c, q) < 1e-4f || legClean(ax, ay, c.x, c.y, q.x, q.y))
                            }
                        corner?.let { Triple(corner, q, si) }
                    }
                if (hit != null) {
                    rv = wireCorner(ax, ay, hit.first, hit.second, hit.third)
                    break
                }
            }
            if (rv < 0) {
                val (ax, ay) = first
                val clean = projections(ax, ay).firstOrNull { (q, _, _) -> legClean(ax, ay, ax, ay, q.x, q.y) }
                rv = addVertex(ax, ay)
                if (clean != null) {
                    val (q, si, _) = clean
                    val qi = addVertex(q.x, q.y)
                    link(rv, qi)
                    val (a, b) = segments[si]
                    val ai = vertices.indexOf(a)
                    val bi = vertices.indexOf(b)
                    if (ai >= 0) link(qi, ai)
                    if (bi >= 0) link(qi, bi)
                    onSegment.getOrPut(si) { mutableListOf() }.add(tOnSegment(q, a, b) to qi)
                } else {
                    val nv = nearestVertex(vertices.subList(0, n0), ax, ay)
                    if (nv >= 0) link(rv, nv)
                }
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

    /** Путь по графу одного этажа между attach-цепочками: последовательность точек графа (без концов). */
    private fun floorPath(
        floor: FloorMap,
        attach: List<List<Pair<Float, Float>>>,
    ): List<MapPoint>? {
        val (vertices, adjacency, attachIdx) = buildGraph(floor, attach)
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
        val graph =
            floorPath(
                floor,
                listOf(attachCandidates(from, floor), attachCandidates(to, floor)),
            ) ?: return null
        val raw = mutableListOf(MapPoint(from.x, from.y)) + graph + MapPoint(to.x, to.y)
        val points = mutableListOf<MapPoint>()
        raw.forEach { if (points.isEmpty() || distP(points.last(), it) > 1e-4f) points.add(it) }
        var total = 0f
        for (i in 1 until points.size) total += distP(points[i - 1], points[i])
        return Route(points, total)
    }

    /**
     * Y7: цепочка кандидатов attach: дверь (разметка) → ближайшая точка контура комнаты
     * к коридору (двери нет; НЕ центр — из центра перпендикуляр в коридор длиннее и чаще
     * задевает соседние кабинеты) → центр (фолбэк, крыло без коридоров рядом).
     */
    private fun attachCandidates(
        r: MapRoom,
        floor: FloorMap,
    ): List<Pair<Float, Float>> {
        r.door?.let { return listOf(it.x to it.y) }
        val contour = nearestContourPoint(r, floor)
        return if (contour != null) listOf(contour, r.x to r.y) else listOf(r.x to r.y)
    }

    /** Точка контура комнаты (семплы граней), ближайшая к любому сегменту коридора; null — коридоров нет. */
    private fun nearestContourPoint(
        r: MapRoom,
        floor: FloorMap,
    ): Pair<Float, Float>? {
        val x0 = r.x - r.w / 2f
        val x1 = r.x + r.w / 2f
        val y0 = r.y - r.h / 2f
        val y1 = r.y + r.h / 2f
        val contour = ArrayList<Pair<Float, Float>>(36)
        for (i in 0..8) {
            val t = i / 8f
            contour.add(x0 + (x1 - x0) * t to y0)
            contour.add(x0 + (x1 - x0) * t to y1)
            contour.add(x0 to y0 + (y1 - y0) * t)
            contour.add(x1 to y0 + (y1 - y0) * t)
        }
        var best: Pair<Float, Float>? = null
        var bestD = Float.MAX_VALUE
        for (c in floor.corridors) {
            for (i in 1 until c.points.size) {
                val a = Vertex(c.points[i - 1].x, c.points[i - 1].y)
                val b = Vertex(c.points[i].x, c.points[i].y)
                for ((px, py) in contour) {
                    val q = projectPointOnSegment(Vertex(px, py), a, b) ?: continue
                    val d = dist(Vertex(px, py), q)
                    if (d < bestD) {
                        bestD = d
                        best = px to py
                    }
                }
            }
        }
        return best
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
            val attach = ArrayList<List<Pair<Float, Float>>>()
            if (fid == fromId) attach.add(attachCandidates(from, floor))
            if (fid == toId) attach.add(attachCandidates(to, floor))
            val (verts, adj, attachIdx) = buildGraph(floor, attach)
            val base = gVerts.size
            verts.forEach { (x, y) -> addV(fid, x, y) }
            adj.forEachIndexed { i, edges -> edges.forEach { (j, d) -> if (j > i) linkV(base + i, base + j, d) } }

            // Y7: snap лестницы — ближайшая ЧИСТАЯ вершина (ребро не пробивает кабинеты);
            // грязный снап (впритирку к углу кабинета) резал углы — жалоба «сквозь кабинеты».
            val roomRects = floor.rooms
                .filter { it.status != "nonexistent" && it.status != "passage" && it.w > 0f && it.h > 0f }
                .map { floatArrayOf(it.x - it.w / 2f, it.y - it.h / 2f, it.x + it.w / 2f, it.y + it.h / 2f) }

            fun snap(x: Float, y: Float): Int {
                // Ближайшая вершина этажного графа к точке (лестница/комната)
                var best = -1
                var bd = Float.MAX_VALUE
                var bestClean = -1
                var bdClean = Float.MAX_VALUE
                verts.forEachIndexed { i, v ->
                    val d = (v.x - x) * (v.x - x) + (v.y - y) * (v.y - y)
                    if (d < bd) {
                        bd = d
                        best = i
                    }
                    if (d < bdClean &&
                        roomRects.none { r -> !rectContains(r, x, y) && segCrossesRect(x, y, v.x, v.y, r) }
                    ) {
                        bdClean = d
                        bestClean = i
                    }
                }
                val pick = if (bestClean >= 0) bestClean else best
                return if (pick < 0) -1 else base + pick
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
        // X23: центр кабинета дописывается к началу/концу ТОЛЬКО если дверь не размечена
        // (door == null). Иначе путь стартует/финиширует В ДВЕРИ (attach-точка = дверь) — без
        // лишнего колена центр→дверь. Когда door == null, attach-точка и есть центр, поэтому
        // дописка — no-op по distP, но оставлена как гарантия (attach-вершина могла не попасть
        // в результат, если путь вырожден).
        if (result.isNotEmpty()) {
            if (from.door == null) {
                val first = result[0]
                if (first.points.isEmpty() || distP(first.points.first(), MapPoint(from.x, from.y)) > 1e-4f) {
                    result[0] = first.copy(points = listOf(MapPoint(from.x, from.y)) + first.points)
                }
            }
            if (to.door == null) {
                val last = result[result.size - 1]
                if (last.points.isEmpty() || distP(last.points.last(), MapPoint(to.x, to.y)) > 1e-4f) {
                    result[result.size - 1] = last.copy(points = last.points + MapPoint(to.x, to.y))
                }
            }
        }
        var total = 0f
        result.forEach { fp -> for (i in 1 until fp.points.size) total += distP(fp.points[i - 1], fp.points[i]) }
        total += STAIR_COST * (result.size - 1) // межэтажные переходы учтены в стоимости, не в геометрии
        return MultiFloorRoute(result, total)
    }

    /**
     * План-аспект (width/height): все три плана 1376×768. Ортогональность маршрута проверяется
     * и строится в ЭКРАННЫХ/plan-px (x·aspect, y), а не в нормализованных координатах.
     */
    const val PLAN_ASPECT = 1376f / 768f

    /**
     * Y7: «почти осевой» сегмент: min/max отношение сторон в plan-px < [SNAP_RATIO] (~11°) —
     * дрожание коридоров, размеченных на глаз (замер: ≤0.16); круче — настоящая диагональ
     * (замер: переходы между коридорами ≥0.24) → L-уголок.
     */
    private const val SNAP_RATIO = 0.2f

    /**
     * W39c/Y7: ортогонализация маршрута для отрисовки в plan-px (x·[aspect]). НИ ОДНОЙ диагонали
     * в итоге: сегмент с дрожанием защёлкивается на доминантную ось (b.y=a.y / b.x=a.x, снап
     * продолжается по полилинии); настоящая диагональ заменяется L-уголком (x1,y2)/(x2,y1) —
     * выбирается уголок, чьи ноги НЕ пересекают rect'ы комнат (status != nonexistent/passage;
     * комнаты концов сегмента не считаются); оба пробивают — уголок с МЕНЬШИМ числом пробоев
     * (диагональ не оставляем никогда). Уже ортогональные сегменты не трогаются; при равном
     * выборе продолжается ось предыдущего сегмента (меньше зигзагов).
     */
    fun orthogonalize(
        points: List<MapPoint>,
        rooms: List<MapRoom>,
        aspect: Float = PLAN_ASPECT,
    ): List<MapPoint> {
        if (points.size < 2) return points
        val rects = roomRects(rooms)

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
        fun countCross(a: MapPoint, b: MapPoint, rs: List<FloatArray>): Int = rs.count { crosses(a, b, it) }

        val out = mutableListOf(points.first())
        var lastLegHorizontal = false // ось последнего сегмента (для выбора уголка)
        for (i in 1 until points.size) {
            val a = out.last() // снап предыдущего сегмента продолжает полилинию
            var b = points[i]
            val dx = Math.abs(b.x - a.x) * aspect
            val dy = Math.abs(b.y - a.y)
            if (dx < ORTHO_EPS && dy < ORTHO_EPS) continue
            if (Math.min(dx, dy) < ORTHO_EPS) {
                out.add(b)
                lastLegHorizontal = dy < ORTHO_EPS
                continue
            }
            if (Math.min(dx, dy) / Math.max(dx, dy) < SNAP_RATIO) {
                // дрожание разметки — защёлка на доминантную ось plan-px, без уголка
                b = if (dx > dy) MapPoint(b.x, a.y) else MapPoint(a.x, b.y)
                out.add(b)
                lastLegHorizontal = dx > dy
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
                // X26/Y7: оба уголка blocked — берём тот, чьи ноги пробивают МЕНЬШЕ комнат;
                // диагональ НЕ оставляем никогда (заказчик: только параллельно-перпендикулярные).
                else -> {
                    val c1Count = countCross(a, c1, active) + countCross(c1, b, active)
                    val c2Count = countCross(a, c2, active) + countCross(c2, b, active)
                    if (c1Count <= c2Count) c1 else c2
                }
            }
            out.add(corner)
            out.add(b)
            lastLegHorizontal = corner == c1 // у c1 последняя нога — горизонталь
        }
        return out
    }

    /** Rect'ы кабинетов-стен (status != nonexistent/passage, ненулевые). */
    private fun roomRects(rooms: List<MapRoom>): List<FloatArray> = rooms
        .filter { it.status != "nonexistent" && it.status != "passage" && it.w > 0f && it.h > 0f }
        .map { floatArrayOf(it.x - it.w / 2f, it.y - it.h / 2f, it.x + it.w / 2f, it.y + it.h / 2f) }

    /**
     * Y7: гарантия «не сквозь кабинеты» — пост-обработка ортогонализованного пути: осевой
     * сегмент, всё ещё пробивающий кабинет, обходится сдвигом на ближайшую свободную ось
     * КОРИДОРА (a→c1→c2→b, все три ноги чистые; оси — параллельные сегменты коридоров по
     * возрастанию расстояния). Обхода нет — сегмент остаётся (route-check помечает пробой).
     */
    fun avoidRooms(
        points: List<MapPoint>,
        rooms: List<MapRoom>,
        corridors: List<MapCorridor>,
        aspect: Float = PLAN_ASPECT,
    ): List<MapPoint> {
        if (points.size < 2) return points
        val rects = roomRects(rooms)

        fun contains(r: FloatArray, p: MapPoint): Boolean =
            p.x >= r[0] && p.x <= r[2] && p.y >= r[1] && p.y <= r[3]

        fun clean(a: MapPoint, b: MapPoint, active: List<FloatArray>): Boolean =
            active.none { segCrossesRect(a.x, a.y, b.x, b.y, it) }

        // оси коридоров (средняя координата поперёк хода) параллельно ориентации сегмента
        val hAxes = ArrayList<Float>()
        val vAxes = ArrayList<Float>()
        corridors.forEach { c ->
            for (i in 1 until c.points.size) {
                val p = c.points[i - 1]
                val q = c.points[i]
                val dx = Math.abs(q.x - p.x) * aspect
                val dy = Math.abs(q.y - p.y)
                if (dx > 0f && dy > 0f && Math.min(dx, dy) / Math.max(dx, dy) >= SNAP_RATIO) continue // диагональ — не ось
                if (dx > dy) hAxes.add((p.y + q.y) / 2f) else vAxes.add((p.x + q.x) / 2f)
            }
        }
        val out = mutableListOf(points.first())
        for (i in 1 until points.size) {
            val a = out.last()
            val b = points[i]
            val active = rects.filter { !contains(it, a) && !contains(it, b) }
            if (clean(a, b, active)) {
                out.add(b)
                continue
            }
            val horizontal = Math.abs(b.y - a.y) <= Math.abs(b.x - a.x) * aspect
            val axes =
                (if (horizontal) hAxes else vAxes)
                    .distinct()
                    .sortedBy { Math.abs(it - if (horizontal) a.y else a.x) }
            val detour =
                axes.firstNotNullOfOrNull { v ->
                    val c1 = if (horizontal) MapPoint(a.x, v) else MapPoint(v, a.y)
                    val c2 = if (horizontal) MapPoint(b.x, v) else MapPoint(v, b.y)
                    if (clean(a, c1, active) && clean(c1, c2, active) && clean(c2, b, active)) listOf(c1, c2) else null
                }
            if (detour != null) {
                out.add(detour[0])
                out.add(detour[1])
            }
            out.add(b) // обхода нет — оставляем, route-check помечает пробой в отчёте
        }
        return out
    }

    /**
     * Y7: ЕДИНЫЙ конвейер полилинии отрисовки: чистка крюков графа → ортогонализация в plan-px
     * (без диагоналей) → обход кабинетов по осям коридоров. Порядок ВАЖЕН: simplify ДО
     * ортогонализации (после неё коллинеарная чистка съедала защёлки уголков и возвращала
     * «кривизну»); после avoidRooms полилиния строго осевая и больше не упрощается.
     * eps чистки ужесточён (1e-4): дефолтный 1e-3 на сыром графе принимал КОРОТКИЕ ПОВОРОТЫ
     * коридора за коллинеарность и спрямлял зигзаг сквозь кабинеты (255–257, 2 этаж).
     */
    fun routePolyline(
        points: List<MapPoint>,
        floor: FloorMap,
        aspect: Float = PLAN_ASPECT,
    ): List<MapPoint> = avoidRooms(
        orthogonalize(simplifyRoute(points, eps = 1e-4f), floor.rooms, aspect),
        floor.rooms,
        floor.corridors,
        aspect,
    )

    private const val ORTHO_EPS = 1e-4f

    /**
     * V23: чистка «крюков» полилинии маршрута до фикс-поинта, три правила на проход:
     * 1) дубли подряд (dist < 1e-4) — убираем;
     * 2) коллинеарная середина: |cross(b-a, c-b)| < [eps] && dot >= 0 — b убираем;
     * 3) шип (возврат назад): c между a и b (dist(a,c) < dist(a,b)) и перпендикуляр
     *    из c к прямой ab < [eps] — b убираем.
     * Концы пути никогда не удаляются. RDP ([rdpEps]) по умолчанию ВЫКЛЮЧЕН (null) —
     * поперёк коридора упрощать нельзя, включается явно при необходимости.
     */
    fun simplifyRoute(points: List<MapPoint>, eps: Float = 1e-3f, rdpEps: Float? = null): List<MapPoint> {
        var pts = if (rdpEps != null) rdp(points, rdpEps) else points
        while (true) {
            val next = simplifyPass(pts, eps)
            if (next.size == pts.size) return next // только удаления — равный размер = фикс-поинт
            pts = next
        }
    }

    private fun simplifyPass(pts: List<MapPoint>, eps: Float): List<MapPoint> {
        // 1) дубли подряд
        val d = ArrayList<MapPoint>(pts.size)
        for (p in pts) if (d.isEmpty() || distP(d.last(), p) >= 1e-4f) d.add(p)
        // 2)+3) потоково: каждая новая точка проверяется как «c» против хвоста (a, b) —
        // удаление b повторно проверяет новый хвост, поэтому каскады схлопываются за проход
        val out = ArrayList<MapPoint>(d.size)
        for (p in d) {
            out.add(p)
            while (out.size >= 3) {
                val a = out[out.size - 3]
                val b = out[out.size - 2]
                val c = out[out.size - 1]
                val abx = b.x - a.x
                val aby = b.y - a.y
                val cross = abx * (c.y - b.y) - aby * (c.x - b.x)
                val dot = abx * (c.x - b.x) + aby * (c.y - b.y)
                val collinear = Math.abs(cross) < eps && dot >= 0f
                val abLen = distP(a, b)
                val perp = if (abLen < 1e-6f) distP(a, c) else Math.abs(abx * (c.y - a.y) - aby * (c.x - a.x)) / abLen
                val spike = distP(a, c) < abLen && perp < eps
                if (collinear || spike) out.removeAt(out.size - 2) else break
            }
        }
        return out
    }

    /** RDP (Рамер–Дуглас–Пекер), итеративно через стек; вызывается только при явном rdpEps. */
    private fun rdp(pts: List<MapPoint>, eps: Float): List<MapPoint> {
        if (pts.size < 3) return pts
        val keep = BooleanArray(pts.size)
        keep[0] = true
        keep[pts.size - 1] = true
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, pts.size - 1))
        while (stack.isNotEmpty()) {
            val (s, e) = stack.removeLast()
            if (e - s < 2) continue
            val a = pts[s]
            val b = pts[e]
            val len = distP(a, b)
            var maxD = -1f
            var idx = -1
            for (i in s + 1 until e) {
                val d = if (len < 1e-6f) distP(a, pts[i]) else Math.abs((b.x - a.x) * (pts[i].y - a.y) - (b.y - a.y) * (pts[i].x - a.x)) / len
                if (d > maxD) {
                    maxD = d
                    idx = i
                }
            }
            if (maxD > eps && idx > 0) {
                keep[idx] = true
                stack.addLast(intArrayOf(s, idx))
                stack.addLast(intArrayOf(idx, e))
            }
        }
        return pts.filterIndexed { i, _ -> keep[i] }
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

    /** X26: точка (x,y) внутри rect r=[x0,y0,x1,y1] (граница считается внутри). */
    private fun rectContains(r: FloatArray, x: Float, y: Float): Boolean =
        x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3]

    /**
     * X26: пересекает ли отрезок (ax,ay)→(bx,by) прямоугольник r=[x0,y0,x1,y1] — Liang–Barsky.
     * Прямоугольник сжимается на [eps] с каждой стороны, чтобы касание стены (grazing) не
     * считалось проходом сквозь комнату. Возвращает true, если непустая часть отрезка лежит
     * внутри сжатого прямоугольника.
     */
    internal fun segCrossesRect(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        r: FloatArray,
        eps: Float = 1e-3f,
    ): Boolean {
        val x0 = r[0] + eps
        val y0 = r[1] + eps
        val x1 = r[2] - eps
        val y1 = r[3] - eps
        if (x1 <= x0 || y1 <= y0) return false
        val dx = bx - ax
        val dy = by - ay
        var t0 = 0f
        var t1 = 1f
        val p = floatArrayOf(-dx, dx, -dy, dy)
        val q = floatArrayOf(ax - x0, x1 - ax, ay - y0, y1 - ay)
        for (i in 0..3) {
            val pi = p[i]
            val qi = q[i]
            if (Math.abs(pi) < 1e-9f) {
                if (qi < 0f) return false // параллельно грани и снаружи полосы
            } else {
                val t = qi / pi
                if (pi < 0f) {
                    if (t > t1) return false
                    if (t > t0) t0 = t
                } else {
                    if (t < t0) return false
                    if (t < t1) t1 = t
                }
            }
        }
        return t0 <= t1
    }

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
