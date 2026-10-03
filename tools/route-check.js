#!/usr/bin/env node
// W39d: проверка достижимости «от каждого к каждому» по college_map.json.
// Порт алгоритма приложения (CollegeRouteFinder, W39a/b): тот же граф этажа
// (узлы+коридоры+проекционная склейка), тот же глобальный граф по всем этажам,
// лестницы сопрягаются по ЯВНЫМ entrance.links (фолбэк — близость ≤0.06 только
// для лестниц БЕЗ links на соседних этажах), один Дейкстра.
// Y7: attach-цепочки (дверь ?: контур ?: центр) с чистым ортогональным Г-подходом,
// чистая проекционная склейка и чистый snap лестниц; отрисованные полилинии
// (simplify→orthogonalize в plan-px→avoidRooms) проверяются на wall-cross=0 и диагонали=0.
//
// Запуск: node tools/route-check.js
// Отчёт: консоль + tools/route-check-report.txt
const fs = require('fs');
const path = require('path');

const MAP = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'college_map.json');
const REPORT = path.join(__dirname, 'route-check-report.txt');
const FLOOR_ORDER = ['1', '2', '3-4']; // CollegeMap.Floor.entries

const JOIN_RADIUS = 0.05, PROJECTION_RADIUS = 0.05, VDUP = 1e-4;
const STAIR_PAIR_RADIUS = 0.06, STAIR_COST = 0.25;
// Y7: план-аспект (все планы 1376×768) — ортогональность в plan-px; «почти осевой» = <0.2
// (замер по данным: дрожание разметки ≤0.16, реальные диагональные переходы ≥0.24)
const PLAN_ASPECT = 1376 / 768, SNAP_RATIO = 0.2;

const raw = JSON.parse(fs.readFileSync(MAP, 'utf8'));
const floors = {};
for (const fid of FLOOR_ORDER) {
  const f = (raw.floors || {})[fid] || {};
  floors[fid] = {
    rooms: (f.rooms || []).map(r => ({
      number: r.number || '', status: r.status || 'normal',
      x: +r.x, y: +r.y, w: +r.w, h: +r.h,
      door: Array.isArray(r.door) && r.door.length >= 2 ? [+r.door[0], +r.door[1]] : null,
    })),
    corridors: (f.corridors || []).filter(c => Array.isArray(c.points) && c.points.length >= 2),
    nodes: (f.nodes || []).map(n => ({ id: n.id, x: +n.x, y: +n.y, links: n.links || [] })),
    entrances: (f.entrances || []).map(e => ({
      id: e.id, kind: e.kind || 'backup', usable: e.usable !== false,
      x: +e.x, y: +e.y, links: Array.isArray(e.links) ? e.links.filter(x => typeof x === 'string') : [],
    })),
  };
}

function floorForRoom(number) {
  const d = String(number || '').trim().replace(/^0+/, '');
  const c = d[0];
  if (c === '1') return '1';
  if (c === '2') return '2';
  if (c === '3' || c === '4') return '3-4';
  return null;
}

// ---- порт buildGraph (CollegeRouteFinder.buildGraph) ----
// attach (W40a): точки старта/финиша — ПРОЕКЦИЯ на ближайший сегмент коридора
// (перпендикуляр без радиуса; если проекция дальше ближайшей вершины — фолбэк на неё)
function buildGraphFloor(fd, attach) {
  attach = attach || [];
  const vertices = [], adjacency = [], segments = [];
  function addVertex(x, y) {
    for (let i = 0; i < vertices.length; i++)
      if (Math.abs(vertices[i].x - x) < VDUP && Math.abs(vertices[i].y - y) < VDUP) return i;
    vertices.push({ x, y }); adjacency.push([]);
    return vertices.length - 1;
  }
  function link(a, b) {
    if (a === b) return;
    const d = Math.hypot(vertices[a].x - vertices[b].x, vertices[a].y - vertices[b].y);
    adjacency[a].push([b, d]); adjacency[b].push([a, d]);
  }
  const nodeIndex = {};
  fd.nodes.forEach(nd => { nodeIndex[nd.id] = addVertex(nd.x, nd.y); });
  fd.nodes.forEach(nd => {
    const a = nodeIndex[nd.id]; if (a == null) return;
    nd.links.forEach(o => { if (nodeIndex[o] != null) link(a, nodeIndex[o]); });
  });
  fd.corridors.forEach(c => {
    const idx = c.points.map(p => addVertex(p[0], p[1]));
    for (let i = 1; i < idx.length; i++) {
      link(idx[i - 1], idx[i]);
      segments.push([vertices[idx[i - 1]], vertices[idx[i]]]);
    }
    [idx[0], idx[idx.length - 1]].forEach(end => {
      let best = -1, bestD = JOIN_RADIUS;
      Object.keys(nodeIndex).forEach(k => {
        const ni = nodeIndex[k];
        const d = Math.hypot(vertices[end].x - vertices[ni].x, vertices[end].y - vertices[ni].y);
        if (d < bestD) { bestD = d; best = ni; }
      });
      if (best >= 0) link(end, best);
    });
  });
  // Проекционная склейка (снимок вершин до цикла, как в приложении).
  // W40b: проекции одного сегмента записываются для цепочки по t.
  // Y7: склейка, пробивающая комнату, НЕ создаётся (зеркало CollegeRouteFinder.buildGraph).
  const wallRects = fd.rooms
    .filter(r => r.status !== 'nonexistent' && r.status !== 'passage' && r.w > 0 && r.h > 0)
    .map(r => [r.x - r.w / 2, r.y - r.h / 2, r.x + r.w / 2, r.y + r.h / 2]);
  const onSegment = {}; // si -> [{t, v}]
  const snapshot = vertices.slice();
  for (const v of snapshot) {
    segments.forEach((seg, si) => {
      const q = projectOnSegment(v, seg[0], seg[1]);
      if (!q) return;
      if (Math.hypot(v.x - q.x, v.y - q.y) < VDUP) return;
      if (wallRects.some(r => !rectContains(r, v.x, v.y) && !rectContains(r, q.x, q.y) &&
          segCrossesRect(v.x, v.y, q.x, q.y, r))) return;
      const qv = addVertex(q.x, q.y);
      link(vertices.indexOf(v), qv);
      const ai = vertices.indexOf(seg[0]), bi = vertices.indexOf(seg[1]);
      if (ai >= 0 && vertices[ai] !== q) link(qv, ai);
      if (bi >= 0 && vertices[bi] !== q) link(qv, bi);
      (onSegment[si] ||= []).push({ t: tOnSegment(q, seg[0], seg[1]), v: qv });
    });
  }
  // W40a/X26/Y7: attach-ЦЕПОЧКИ (дверь ?: контур ?: центр). 1) чистый ортогональный Г-подход
  // (уголок c: обе ноги не пробивают чужие комнаты; q скользит вдоль коридора); 2) прямая чистая
  // проекция первой точки цепочки (X26); 3) фолбэк — ближайшая «настоящая» вершина.
  const n0 = vertices.length;
  const attachIdx = attach.map(chain => {
    const legClean = (ax, ay, x0, y0, x1, y1) =>
      !wallRects.some(r => !rectContains(r, ax, ay) && segCrossesRect(x0, y0, x1, y1, r));
    const projections = (ax, ay) => {
      const cands = [];
      for (let si = 0; si < segments.length; si++) {
        const q = projectPointOnSegment([ax, ay], segments[si][0], segments[si][1]);
        if (q) cands.push({ q, si, d: Math.hypot(ax - q.x, ay - q.y) });
      }
      cands.sort((a, b) => a.d - b.d);
      return cands;
    };
    const wireCorner = (ax, ay, corner, q, si) => {
      const rv = addVertex(ax, ay);
      const ci = addVertex(corner[0], corner[1]);
      if (ci !== rv) link(rv, ci);
      const qi = addVertex(q.x, q.y);
      if (qi !== ci) link(ci, qi);
      const seg = segments[si];
      const ai = vertices.indexOf(seg[0]), bi = vertices.indexOf(seg[1]);
      if (ai >= 0) link(qi, ai);
      if (bi >= 0) link(qi, bi);
      (onSegment[si] ||= []).push({ t: tOnSegment(q, seg[0], seg[1]), v: qi });
      return rv;
    };
    let rv = -1;
    for (const [ax, ay] of chain) {
      let hit = null;
      for (const { q, si } of projections(ax, ay)) {
        const corner = [[ax, q.y], [q.x, ay]].find(c =>
          (Math.hypot(ax - c[0], ay - c[1]) < 1e-4 || legClean(ax, ay, ax, ay, c[0], c[1])) &&
          (Math.hypot(c[0] - q.x, c[1] - q.y) < 1e-4 || legClean(ax, ay, c[0], c[1], q.x, q.y)));
        if (corner) { hit = { corner, q, si }; break; }
      }
      if (hit) { rv = wireCorner(ax, ay, hit.corner, hit.q, hit.si); break; }
    }
    if (rv < 0) {
      const [ax, ay] = chain[0];
      const clean = projections(ax, ay).find(c => legClean(ax, ay, ax, ay, c.q.x, c.q.y));
      rv = addVertex(ax, ay);
      if (clean) {
        const qi = addVertex(clean.q.x, clean.q.y);
        link(rv, qi);
        const seg = segments[clean.si];
        const ai = vertices.indexOf(seg[0]), bi = vertices.indexOf(seg[1]);
        if (ai >= 0) link(qi, ai);
        if (bi >= 0) link(qi, bi);
        (onSegment[clean.si] ||= []).push({ t: tOnSegment(clean.q, seg[0], seg[1]), v: qi });
      } else {
        const nv = nearestVertex(vertices.slice(0, n0), ax, ay);
        if (nv >= 0) link(rv, nv);
      }
    }
    return rv;
  });
  // W40b: проекции одного сегмента — ЦЕПОЧКОЙ по t (a—q1—q2—…—b): без этого две двери на
  // одном длинном «на глаз»-сегменте связаны только через дальние концы (крюк к перекрёстку)
  Object.keys(onSegment).forEach(si => {
    const seg = segments[si];
    const chain = [vertices.indexOf(seg[0])];
    onSegment[si].sort((p, q) => p.t - q.t).forEach(e => { if (e.v !== chain[chain.length - 1]) chain.push(e.v); });
    const bi = vertices.indexOf(seg[1]);
    if (bi !== chain[chain.length - 1]) chain.push(bi);
    for (let i = 1; i < chain.length; i++) link(chain[i - 1], chain[i]);
  });
  return { vertices, adjacency, attach: attachIdx };
}
function tOnSegment(q, a, b) { // параметр t точки q на отрезке ab (0 = a, 1 = b)
  const dx = b.x - a.x, dy = b.y - a.y, len2 = dx * dx + dy * dy;
  if (len2 === 0) return 0;
  return ((q.x - a.x) * dx + (q.y - a.y) * dy) / len2;
}
function projectPointOnSegment(p, a, b) { // t зажат в [0,1], без ограничения радиуса
  const dx = b.x - a.x, dy = b.y - a.y, len2 = dx * dx + dy * dy;
  if (len2 === 0) return null;
  let t = ((p[0] - a.x) * dx + (p[1] - a.y) * dy) / len2;
  t = Math.max(0, Math.min(1, t));
  return { x: a.x + t * dx, y: a.y + t * dy };
}
function nearestVertex(vertices, x, y) {
  let best = -1, bd = Infinity;
  for (let i = 0; i < vertices.length; i++) {
    const d = (vertices[i].x - x) * (vertices[i].x - x) + (vertices[i].y - y) * (vertices[i].y - y);
    if (d < bd) { bd = d; best = i; }
  }
  return best;
}
function projectOnSegment(p, a, b) {
  const dx = b.x - a.x, dy = b.y - a.y, len2 = dx * dx + dy * dy;
  if (len2 === 0) return null;
  let t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2;
  t = Math.max(0, Math.min(1, t));
  const q = { x: a.x + t * dx, y: a.y + t * dy };
  return Math.hypot(p.x - q.x, p.y - q.y) <= PROJECTION_RADIUS ? q : null;
}
// X26: зеркало CollegeRouteFinder.rectContains / segCrossesRect (Liang–Barsky).
function rectContains(r, x, y) { return x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3]; }
function segCrossesRect(ax, ay, bx, by, r, eps) {
  if (eps == null) eps = 1e-3;
  const x0 = r[0] + eps, y0 = r[1] + eps, x1 = r[2] - eps, y1 = r[3] - eps;
  if (x1 <= x0 || y1 <= y0) return false;
  const dx = bx - ax, dy = by - ay;
  let t0 = 0, t1 = 1;
  const p = [-dx, dx, -dy, dy];
  const q = [ax - x0, x1 - ax, ay - y0, y1 - ay];
  for (let i = 0; i < 4; i++) {
    if (Math.abs(p[i]) < 1e-9) { if (q[i] < 0) return false; }
    else {
      const t = q[i] / p[i];
      if (p[i] < 0) { if (t > t1) return false; if (t > t0) t0 = t; }
      else { if (t < t0) return false; if (t < t1) t1 = t; }
    }
  }
  return t0 <= t1;
}

// ---- W39b: глобальный граф по ВСЕМ этажам + межэтажные рёбра лестниц ----
const gVerts = [];   // {floor, x, y}
const gAdj = [];
const stairEdges = new Set(); // "min,max" — переходы лестниц (границы сегментов)
function addV(fid, x, y) {
  for (let i = 0; i < gVerts.length; i++)
    if (gVerts[i].floor === fid && Math.abs(gVerts[i].x - x) < VDUP && Math.abs(gVerts[i].y - y) < VDUP) return i;
  gVerts.push({ floor: fid, x, y }); gAdj.push([]);
  return gVerts.length - 1;
}
function linkV(a, b, w) {
  if (a === b) return;
  const d = (w != null) ? w : Math.hypot(gVerts[a].x - gVerts[b].x, gVerts[a].y - gVerts[b].y);
  gAdj[a].push([b, d]); gAdj[b].push([a, d]);
}
const stairVById = {};    // id лестницы -> вершина глобального графа
const stairs = [];        // {fid, en, v}
const roomV = {};         // 'номер' -> вершина (только usable + status!=nonexistent)

// Y7: цепочка attach-кандидатов комнаты: дверь ?: (контур к коридору, центр) — зеркало
// CollegeRouteFinder.attachCandidates/nearestContourPoint.
function nearestContourPoint(r, fd) {
  const x0 = r.x - r.w / 2, x1 = r.x + r.w / 2, y0 = r.y - r.h / 2, y1 = r.y + r.h / 2;
  const contour = [];
  for (let i = 0; i <= 8; i++) {
    const t = i / 8;
    contour.push([x0 + (x1 - x0) * t, y0], [x0 + (x1 - x0) * t, y1], [x0, y0 + (y1 - y0) * t], [x1, y0 + (y1 - y0) * t]);
  }
  let best = null, bestD = Infinity;
  for (const c of fd.corridors) for (let i = 1; i < c.points.length; i++) {
    const a = { x: c.points[i - 1][0], y: c.points[i - 1][1] }, b = { x: c.points[i][0], y: c.points[i][1] };
    for (const p of contour) {
      const q = projectPointOnSegment(p, a, b);
      if (!q) continue;
      const d = Math.hypot(p[0] - q.x, p[1] - q.y);
      if (d < bestD) { bestD = d; best = p; }
    }
  }
  return best;
}
function attachChain(r, fd) {
  if (r.door) return [[r.door[0], r.door[1]]];
  const c = nearestContourPoint(r, fd);
  return c ? [c, [r.x, r.y]] : [[r.x, r.y]];
}

FLOOR_ORDER.forEach(fid => {
  const fd = floors[fid];
  if (!fd) return;
  // W40a/Y7: комнаты — attach-ЦЕПОЧКИ (дверь ?: контур+центр) → чистый Г/проекция/фолбэк
  const rooms = fd.rooms.filter(r => r.number && r.status !== 'nonexistent' && floorForRoom(r.number));
  const attach = rooms.map(r => attachChain(r, fd));
  const attachNumbers = rooms.map(r => r.number.toLowerCase());
  // X26: главный вход — тоже attach-цепочка (одна точка), зеркалит mainEntranceRoom в Kotlin.
  if (fid === '1') {
    const main = (fd.entrances || []).find(e => e.kind === 'main');
    if (main) { attach.push([[main.x, main.y]]); attachNumbers.push('100'); }
  }
  const g = buildGraphFloor(fd, attach);
  const base = gVerts.length;
  g.vertices.forEach(v => addV(fid, v.x, v.y));
  g.adjacency.forEach((edges, i) => edges.forEach(ed => { if (ed[0] > i) linkV(base + i, base + ed[0], ed[1]); }));

  // Y7: snap — ближайшая ЧИСТАЯ вершина (ребро не пробивает кабинеты), фолбэк — ближайшая
  const roomRects = fd.rooms
    .filter(r => r.status !== 'nonexistent' && r.status !== 'passage' && r.w > 0 && r.h > 0)
    .map(r => [r.x - r.w / 2, r.y - r.h / 2, r.x + r.w / 2, r.y + r.h / 2]);
  function snap(x, y) {
    let best = -1, bd = Infinity, bestClean = -1, bdClean = Infinity;
    g.vertices.forEach((v, i) => {
      const d = (v.x - x) * (v.x - x) + (v.y - y) * (v.y - y);
      if (d < bd) { bd = d; best = i; }
      if (d < bdClean && !roomRects.some(r => !rectContains(r, x, y) && segCrossesRect(x, y, v.x, v.y, r))) {
        bdClean = d; bestClean = i;
      }
    });
    const pick = bestClean >= 0 ? bestClean : best;
    return pick < 0 ? -1 : base + pick;
  }

  fd.entrances.filter(e => e.kind === 'stairs' && e.usable).forEach(e => {
    const sv = addV(fid, e.x, e.y);
    const near = snap(e.x, e.y);
    if (near >= 0) linkV(sv, near);
    stairVById[e.id] = sv;
    stairs.push({ fid, en: e, v: sv });
  });
  attachNumbers.forEach((num, k) => { roomV[num] = base + g.attach[k]; });
});

// 1) ЯВНЫЕ links: пары по id (любые этажи, включая совпадение floorId — 3-4 план
//    несёт 3-й и 4-й этажи: лестница «у 335» связывается с площадкой 4-го этажа).
// 2) Фолбэк близости ≤ STAIR_PAIR_RADIUS — только для лестниц БЕЗ links на СОСЕДНИХ этажах.
function stairEdgeKey(a, b) { return a < b ? a + ',' + b : b + ',' + a; }
function addStairEdge(a, b) {
  if (a === b || stairEdges.has(stairEdgeKey(a, b))) return; // links симметричны — не дублируем
  linkV(a, b, STAIR_COST);
  stairEdges.add(stairEdgeKey(a, b));
}
for (const s of stairs) {
  if (!s.en.links.length) continue;
  for (const tid of s.en.links) {
    const v = stairVById[tid];
    if (v != null) addStairEdge(s.v, v);
  }
}
FLOOR_ORDER.forEach((fid, i) => {
  const next = FLOOR_ORDER[i + 1];
  if (!next) return;
  const here = stairs.filter(s => s.fid === fid && !s.en.links.length);
  const there = stairs.filter(s => s.fid === next && !s.en.links.length);
  for (const a of here) for (const b of there) {
    if (Math.hypot(a.en.x - b.en.x, a.en.y - b.en.y) <= STAIR_PAIR_RADIUS) addStairEdge(a.v, b.v);
  }
});

// ---- Дейкстра (порт shortestPathGlobal) ----
function dijkstra(start, finish) {
  const n = gVerts.length;
  const best = new Array(n).fill(Infinity), prev = new Array(n).fill(-1), used = new Array(n).fill(false);
  best[start] = 0;
  for (let k = 0; k < n; k++) {
    let v = -1, bd = Infinity;
    for (let i = 0; i < n; i++) if (!used[i] && best[i] < bd) { bd = best[i]; v = i; }
    if (v < 0) break;
    used[v] = true;
    for (const ed of gAdj[v]) if (best[v] + ed[1] < best[ed[0]]) { best[ed[0]] = best[v] + ed[1]; prev[ed[0]] = v; }
  }
  if (best[finish] === Infinity) return null;
  const p = []; let cur = finish;
  while (cur >= 0) { p.unshift(cur); if (cur === start) break; cur = prev[cur]; }
  return p[0] === start ? p : null;
}

// ---- findMultiFloorRoute (порт: сегменты по floorId + рёбрам лестниц) ----
function routeLength(segments) { // как в приложении: геометрия + STAIR_COST на переход
  let len = 0;
  for (const sg of segments) for (let i = 1; i < sg.points.length; i++)
    len += Math.hypot(sg.points[i][0] - sg.points[i - 1][0], sg.points[i][1] - sg.points[i - 1][1]);
  return len + STAIR_COST * (segments.length - 1);
}
function findMultiFloorRoute(fromNumber, toNumber) {
  const fromId = floorForRoom(fromNumber), toId = floorForRoom(toNumber);
  if (!fromId || !toId) return null;
  const sv = roomV[String(fromNumber).toLowerCase()], fv = roomV[String(toNumber).toLowerCase()];
  if (sv == null || fv == null) return null;
  const path = dijkstra(sv, fv);
  if (!path) return null;
  const segments = [];
  let curFloor = gVerts[path[0]].floor, cur = [];
  for (let i = 0; i < path.length; i++) {
    const v = gVerts[path[i]];
    const isStairEdge = i > 0 && stairEdges.has(stairEdgeKey(path[i - 1], path[i]));
    if (v.floor !== curFloor || isStairEdge) {
      segments.push({ floorId: curFloor, points: cur });
      curFloor = v.floor; cur = [];
    }
    cur.push([v.x, v.y]);
  }
  segments.push({ floorId: curFloor, points: cur });
  return segments;
}

// X26: псевдо-комната «главный вход» (roomV['100']) теперь подключается ЧИСТОЙ ПРОЕКЦИЕЙ
// внутри этажного цикла (attach-точка на 1 этаже) — зеркалит mainEntranceRoom в приложении.
// Отдельный snap-хак удалён: он давал неконтролируемое ребро, которое могло пробивать комнату.

/* ===== Y7: порт orthogonalize/avoidRooms (plan-px, без диагоналей) — зеркало CollegeRouteFinder ===== */
const ORTHO_EPS = 1e-4;
function roomRectsOf(fd) {
  return fd.rooms.filter(r => r.status !== 'nonexistent' && r.status !== 'passage' && r.w > 0 && r.h > 0)
    .map(r => [r.x - r.w / 2, r.y - r.h / 2, r.x + r.w / 2, r.y + r.h / 2]);
}
function orthogonalize(points, rooms) {
  if (!points || points.length < 2) return points || [];
  const rects = rooms.filter(r => r.status !== 'nonexistent' && r.status !== 'passage' && r.w > 0 && r.h > 0)
    .map(r => [r.x - r.w / 2, r.y - r.h / 2, r.x + r.w / 2, r.y + r.h / 2]);
  const contains = (r, p) => p[0] >= r[0] && p[0] <= r[2] && p[1] >= r[1] && p[1] <= r[3];
  const crosses = (a, b, r) => {
    const minY = Math.min(a[1], b[1]), maxY = Math.max(a[1], b[1]);
    const minX = Math.min(a[0], b[0]), maxX = Math.max(a[0], b[0]);
    return Math.abs(a[1] - b[1]) < ORTHO_EPS
      ? (a[1] > r[1] && a[1] < r[3] && maxX > r[0] && minX < r[2])
      : (a[0] > r[0] && a[0] < r[2] && maxY > r[1] && minY < r[3]);
  };
  const blocked = (a, b, rs) => rs.some(r => crosses(a, b, r));
  const countCross = (a, b, rs) => rs.filter(r => crosses(a, b, r)).length;
  const out = [points[0].slice()];
  let lastLegHorizontal = false;
  for (let i = 1; i < points.length; i++) {
    const a = out[out.length - 1];
    let b = points[i].slice();
    const dx = Math.abs(b[0] - a[0]) * PLAN_ASPECT, dy = Math.abs(b[1] - a[1]);
    if (dx < ORTHO_EPS && dy < ORTHO_EPS) continue;
    if (Math.min(dx, dy) < ORTHO_EPS) { out.push(b); lastLegHorizontal = dy < ORTHO_EPS; continue; }
    if (Math.min(dx, dy) / Math.max(dx, dy) < SNAP_RATIO) { // дрожание разметки — защёлка на ось
      b = dx > dy ? [b[0], a[1]] : [a[0], b[1]];
      out.push(b); lastLegHorizontal = dx > dy; continue;
    }
    const active = rects.filter(r => !contains(r, a) && !contains(r, b));
    const c1 = [a[0], b[1]], c2 = [b[0], a[1]];
    const c1Free = !blocked(a, c1, active) && !blocked(c1, b, active);
    const c2Free = !blocked(a, c2, active) && !blocked(c2, b, active);
    let corner;
    if (c1Free && c2Free) corner = lastLegHorizontal ? c2 : c1;
    else if (c1Free) corner = c1;
    else if (c2Free) corner = c2;
    else { // оба пробивают — с меньшим числом пробоев; диагональ НЕ оставляем
      const c1Count = countCross(a, c1, active) + countCross(c1, b, active);
      const c2Count = countCross(a, c2, active) + countCross(c2, b, active);
      corner = c1Count <= c2Count ? c1 : c2;
    }
    out.push(corner.slice()); out.push(b);
    lastLegHorizontal = corner === c1;
  }
  return out;
}
function avoidRooms(points, fd) {
  if (!points || points.length < 2) return points || [];
  const rects = roomRectsOf(fd);
  const contains = (r, p) => p[0] >= r[0] && p[0] <= r[2] && p[1] >= r[1] && p[1] <= r[3];
  const clean = (a, b, rs) => !rs.some(r => segCrossesRect(a[0], a[1], b[0], b[1], r));
  const hAxes = [], vAxes = [];
  for (const c of fd.corridors) for (let i = 1; i < c.points.length; i++) {
    const p = c.points[i - 1], q = c.points[i];
    const dx = Math.abs(q[0] - p[0]) * PLAN_ASPECT, dy = Math.abs(q[1] - p[1]);
    if (dx > 0 && dy > 0 && Math.min(dx, dy) / Math.max(dx, dy) >= SNAP_RATIO) continue;
    if (dx > dy) hAxes.push((p[1] + q[1]) / 2); else vAxes.push((p[0] + q[0]) / 2);
  }
  const out = [points[0].slice()];
  for (let i = 1; i < points.length; i++) {
    const a = out[out.length - 1], b = points[i].slice();
    const active = rects.filter(r => !contains(r, a) && !contains(r, b));
    if (clean(a, b, active)) { out.push(b); continue; }
    const horizontal = Math.abs(b[1] - a[1]) <= Math.abs(b[0] - a[0]) * PLAN_ASPECT;
    const axes = [...new Set(horizontal ? hAxes : vAxes)]
      .sort((u, v) => Math.abs(u - (horizontal ? a[1] : a[0])) - Math.abs(v - (horizontal ? a[1] : a[0])));
    let detour = null;
    for (const v of axes) {
      const c1 = horizontal ? [a[0], v] : [v, a[1]];
      const c2 = horizontal ? [b[0], v] : [v, b[1]];
      if (clean(a, c1, active) && clean(c1, c2, active) && clean(c2, b, active)) { detour = [c1, c2]; break; }
    }
    if (detour) { out.push(detour[0].slice()); out.push(detour[1].slice()); }
    out.push(b);
  }
  return out;
}
// Полилиния ОТРИСОВКИ сегмента этажа (зеркало CollegeRouteFinder.routePolyline:
// simplify → orthogonalize → avoidRooms; порядок важен, см. комментарий в Kotlin)
function simplifyPass(pts, eps) {
  const d = [];
  for (const p of pts) if (!d.length || Math.hypot(d[d.length - 1][0] - p[0], d[d.length - 1][1] - p[1]) >= 1e-4) d.push(p);
  const out = [];
  for (const p of d) {
    out.push(p);
    while (out.length >= 3) {
      const a = out[out.length - 3], b = out[out.length - 2], c = out[out.length - 1];
      const abx = b[0] - a[0], aby = b[1] - a[1];
      const cross = abx * (c[1] - b[1]) - aby * (c[0] - b[0]);
      const dot = abx * (c[0] - b[0]) + aby * (c[1] - b[1]);
      const collinear = Math.abs(cross) < eps && dot >= 0;
      const abLen = Math.hypot(abx, aby);
      const perp = abLen < 1e-6 ? Math.hypot(a[0] - c[0], a[1] - c[1]) : Math.abs(abx * (c[1] - a[1]) - aby * (c[0] - a[0])) / abLen;
      const spike = Math.hypot(a[0] - c[0], a[1] - c[1]) < abLen && perp < eps;
      if (collinear || spike) out.splice(out.length - 2, 1); else break;
    }
  }
  return out;
}
function simplifyRoute(pts) {
  let cur = pts;
  while (true) {
    // eps 1e-4, как в CollegeRouteFinder.routePolyline: дефолтный 1e-3 съедал короткие повороты
    const next = simplifyPass(cur, 1e-4);
    if (next.length === cur.length) return next;
    cur = next;
  }
}
function drawnPolyline(seg) {
  const fd = floors[seg.floorId];
  if (!fd) return seg.points;
  return avoidRooms(orthogonalize(simplifyRoute(seg.points), fd.rooms), fd);
}

// ---- Матрица достижимости: union-find по тому же графу (веса ≥0, ребра
// неориентированные → достижимость = связность; N² пар без N Дейкстр) ----
const uf = gVerts.map((_, i) => i);
function find(x) { while (uf[x] !== x) { uf[x] = uf[uf[x]]; x = uf[x]; } return x; }
function union(a, b) { const ra = find(a), rb = find(b); if (ra !== rb) uf[ra] = rb; }
gAdj.forEach((edges, i) => edges.forEach(ed => union(i, ed[0])));

// ---- Отчёт ----
const lines = [];
const say = s => { lines.push(s); console.log(s); };

const roomsList = [];
for (const fid of FLOOR_ORDER) for (const r of floors[fid].rooms)
  if (r.number && r.status !== 'nonexistent' && floorForRoom(r.number) === fid)
    roomsList.push({ number: r.number, fid, v: roomV[r.number.toLowerCase()] });

say('W39 route-check: карта ' + MAP);
say('комнат (usable, status!=nonexistent): ' + roomsList.length +
  ' | вершин графа: ' + gVerts.length + ' | лестниц usable: ' + stairs.length +
  ' (с links: ' + stairs.filter(s => s.en.links.length).length + ')');
say('');

let total = 0, ok = 0;
const unreachable = [];
for (let i = 0; i < roomsList.length; i++) {
  for (let j = i + 1; j < roomsList.length; j++) {
    total++;
    if (find(roomsList[i].v) === find(roomsList[j].v)) ok++;
    else unreachable.push(roomsList[i].number + '-' + roomsList[j].number);
  }
}
say('ВСЕГО пар ' + total + ', достижимо ' + ok + ', НЕДОСТИЖИМО ' + unreachable.length);
if (unreachable.length) {
  say('');
  say('Недостижимые пары (по 50 на строку):');
  for (let i = 0; i < unreachable.length; i += 50)
    say('  ' + unreachable.slice(i, i + 50).join(' '));
}
say('');

// Компоненты связности комнат (кто с кем)
const comps = new Map();
for (const r of roomsList) {
  const c = find(r.v);
  if (!comps.has(c)) comps.set(c, []);
  comps.get(c).push(r);
}
say('Компоненты связности: ' + comps.size);
for (const [c, rs] of [...comps.entries()].sort((a, b) => b[1].length - a[1].length)) {
  if (rs.length < roomsList.length) {
    say('  компонента из ' + rs.length + ' комнат: ' +
      rs.slice(0, 20).map(r => r.number).join(' ') + (rs.length > 20 ? ' …' : ''));
  }
}
say('');

// Каждая лестница: с кем связана (links / близость)
say('Лестницы (usable) и их сопряжения:');
for (const s of stairs) {
  const via = s.en.links.length ? 'links' : 'близость';
  const partners = [];
  for (const ed of gAdj[s.v]) {
    if (!stairEdges.has(stairEdgeKey(s.v, ed[0]))) continue;
    const other = stairs.find(t => t.v === ed[0]);
    if (other) partners.push(other.en.id + ' (эт' + other.fid + ')');
  }
  say('  лестница ' + s.en.id + ' (эт' + s.fid + '): ' +
    (partners.length ? '↔ ' + partners.join(', ↔ ') : 'НИ С КЕМ НЕ СВЯЗАНА') +
    ' [' + via + (s.en.links.length ? ' ' + JSON.stringify(s.en.links) : '') + ']');
}
const notLinked = stairs.filter(s => !gAdj[s.v].some(ed => stairEdges.has(stairEdgeKey(s.v, ed[0]))));
if (notLinked.length) {
  say('');
  say('ПРОБЛЕМА: лестницы без сопряжения (маршруты через них невозможны):');
  for (const s of notLinked) say('  ' + s.en.id + ' (эт' + s.fid + ', x=' + s.en.x + ', y=' + s.en.y + ')');
  say('  Совет: свяжите их в редакторе (лестница → «🔗 Связать с этажом…») — явные links');
  say('  имеют приоритет над близостью. Площадка 4-го этажа (каб. 401-405) должна быть');
  say('  связана с лестницей у 335 на том же плане «3-4».');
}

// Демо-маршруты (сегменты по этажам — как рисует приложение)
say('');
say('Демо-маршруты (цепочки этажей):');
const demos = [
  ['331', '301'], ['348', '305'], ['301', '335'], ['401', '405'], ['401', '301'],
  ['131', '241'], ['136', '332'], ['101', '348'], ['238', '241'], ['100', '301'], ['100', '401'],
];
for (const [a, b] of demos) {
  const r = findMultiFloorRoute(a, b);
  say('  ' + a + ' → ' + b + ': ' +
    (r ? r.map(sg => 'эт ' + sg.floorId + ' (' + sg.points.length + ' т.)').join(' → ') : 'НЕТ МАРШРУТА'));
}

// W40: маршруты заказчика (жалобы на крюки и 309→405) — длина и точки
say('');
say('Маршруты заказчика (W40):');
const w40 = [['107', '123'], ['116', '123'], ['331', '301'], ['309', '405']];
for (const [a, b] of w40) {
  const r = findMultiFloorRoute(a, b);
  const pts = r ? r.reduce((n, sg) => n + sg.points.length, 0) : 0;
  say('  ' + a + ' → ' + b + ': ' + (r
    ? 'длина ' + routeLength(r).toFixed(3) + ', точек ' + pts + ' [' +
      r.map(sg => 'эт ' + sg.floorId + ':' + sg.points.length).join(' → ') + ']'
    : 'НЕТ МАРШРУТА'));
}

// X26: счётчик wall-cross — сколько ног маршрута пробивает ЧУЖИЕ комнаты. Для каждой
// door-null комнаты маршрут к главному входу: проверяем каждый отрезок floorPath на
// пересечение rect другой комнаты (своя комната маршрута исключена). Зеркало JVM-теста.
//
// ВАЖНО: это ДИАГНОСТИКА, а не воротa. Ненулевое значение на текущей карте дают:
//  (а) «замурованные» комнаты без двери, стоящие ЗА другой комнатой (напр. 130 за 128,
//      142-2 за 142) — для них НЕТ чистой проекции, срабатывает фолбэк nearestVertex, и
//      нога объективно проходит сквозь соседнюю комнату (геометрия плана, не логика X26);
//  (б) коридоры, размеченные «на глаз» впритирку к углам узких комнат (напр. 132 w=0.013) —
//      эти ноги общие для многих маршрутов и существовали ДО X26.
// Логика X26 (чистая проекция attach) устраняет пробои там, где чистая проекция существует.
// Y7: счётчик wall-cross — ноги ОТРИСОВАННОЙ полилинии (ортогонализация+обход) сквозь ЧУЖИЕ
// кабинеты. Для каждой door-null комнаты маршрут к главному входy; rect'ы статуса passage
// (сквозные залы) стенами не считаются. Зеркало JVM-теста.
function countWallCross(segments, originNumber) {
  let n = 0;
  for (const sg of segments) {
    const fd = floors[sg.floorId];
    if (!fd) continue;
    const poly = drawnPolyline(sg);
    const rects = fd.rooms
      .filter(r => r.status !== 'nonexistent' && r.status !== 'passage' && r.w > 0 && r.h > 0 &&
                   r.number.toLowerCase() !== String(originNumber).toLowerCase())
      .map(r => ({ num: r.number, r: [r.x - r.w / 2, r.y - r.h / 2, r.x + r.w / 2, r.y + r.h / 2] }));
    for (let i = 1; i < poly.length; i++) {
      const a = poly[i - 1], b = poly[i];
      for (const o of rects) if (segCrossesRect(a[0], a[1], b[0], b[1], o.r)) {
        n++;
        if (process.env.WC_DEBUG) console.log('  CROSS ' + originNumber + ' эт' + sg.floorId + ' leg[' +
          a.map(v => v.toFixed(3)) + '→' + b.map(v => v.toFixed(3)) + '] × каб.' + o.num);
        break;
      }
    }
  }
  return n;
}
// Y7: диагонали в итоговых полилиниях (plan-px): сегмент не осевой, если обе стороны ≥ eps.
function countDiagonals(segments) {
  let n = 0;
  for (const sg of segments) {
    const poly = drawnPolyline(sg);
    for (let i = 1; i < poly.length; i++) {
      const dx = Math.abs(poly[i][0] - poly[i - 1][0]) * PLAN_ASPECT;
      const dy = Math.abs(poly[i][1] - poly[i - 1][1]);
      if (dx >= ORTHO_EPS && dy >= ORTHO_EPS) n++;
    }
  }
  return n;
}
const doorNullRooms = [];
for (const fid of FLOOR_ORDER) for (const r of floors[fid].rooms)
  if (r.number && r.status !== 'nonexistent' && floorForRoom(r.number) === fid && !r.door)
    doorNullRooms.push(r.number);
let wallCross = 0, wcRoutes = 0, diag = 0;
const wcRooms = [];
for (const num of doorNullRooms) {
  const segs = findMultiFloorRoute(num, '100');
  if (!segs) continue;
  wcRoutes++;
  const c = countWallCross(segs, num);
  wallCross += c;
  diag += countDiagonals(segs);
  if (c > 0) wcRooms.push(num + '(' + c + ')');
}
for (const [a, b] of demos.concat(w40)) {
  const r = findMultiFloorRoute(a, b);
  if (r) diag += countDiagonals(r);
}
say('');
say('Y7 wall-cross (ноги ОТРИСОВАННЫХ полилиний door-null маршрутов сквозь чужие кабинеты):');
say('  door-null комнат: ' + doorNullRooms.length + ' | маршрутов к входу: ' + wcRoutes +
    ' | wall-cross legs: ' + wallCross + ' | диагоналей в итоговых полилиниях: ' + diag);
say('  комнаты с пробоями: ' + (wcRooms.length ? wcRooms.join(' ') : 'нет'));
say('  Целевые значения: wall-cross=0, диагонали=0 (маршруты строго параллельно-перпендикулярные,');
say('  кабинеты-стены не пробиваются; статус passage = сквозной зал, стеной не считается).');

fs.writeFileSync(REPORT, lines.join('\n') + '\n', 'utf8');
console.log('\nОтчёт записан: ' + REPORT);
