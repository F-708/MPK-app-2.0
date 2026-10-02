#!/usr/bin/env node
// W39d: проверка достижимости «от каждого к каждому» по college_map.json.
// Порт алгоритма приложения (CollegeRouteFinder, W39a/b): тот же граф этажа
// (узлы+коридоры+проекционная склейка), тот же глобальный граф по всем этажам,
// лестницы сопрягаются по ЯВНЫМ entrance.links (фолбэк — близость ≤0.06 только
// для лестниц БЕЗ links на соседних этажах), один Дейкстра.
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

const raw = JSON.parse(fs.readFileSync(MAP, 'utf8'));
const floors = {};
for (const fid of FLOOR_ORDER) {
  const f = (raw.floors || {})[fid] || {};
  floors[fid] = {
    rooms: (f.rooms || []).map(r => ({
      number: r.number || '', status: r.status || 'normal',
      x: +r.x, y: +r.y, w: +r.w, h: +r.h,
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
function buildGraphFloor(fd) {
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
  // Проекционная склейка (снимок вершин до цикла, как в приложении)
  const snapshot = vertices.slice();
  for (const v of snapshot) {
    for (const seg of segments) {
      const q = projectOnSegment(v, seg[0], seg[1]);
      if (!q) continue;
      if (Math.hypot(v.x - q.x, v.y - q.y) < VDUP) continue;
      const qv = addVertex(q.x, q.y);
      link(vertices.indexOf(v), qv);
      const ai = vertices.indexOf(seg[0]), bi = vertices.indexOf(seg[1]);
      if (ai >= 0 && vertices[ai] !== q) link(qv, ai);
      if (bi >= 0 && vertices[bi] !== q) link(qv, bi);
    }
  }
  return { vertices, adjacency };
}
function projectOnSegment(p, a, b) {
  const dx = b.x - a.x, dy = b.y - a.y, len2 = dx * dx + dy * dy;
  if (len2 === 0) return null;
  let t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2;
  t = Math.max(0, Math.min(1, t));
  const q = { x: a.x + t * dx, y: a.y + t * dy };
  return Math.hypot(p.x - q.x, p.y - q.y) <= PROJECTION_RADIUS ? q : null;
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

FLOOR_ORDER.forEach(fid => {
  const fd = floors[fid];
  if (!fd) return;
  const g = buildGraphFloor(fd);
  const base = gVerts.length;
  g.vertices.forEach(v => addV(fid, v.x, v.y));
  g.adjacency.forEach((edges, i) => edges.forEach(ed => { if (ed[0] > i) linkV(base + i, base + ed[0], ed[1]); }));

  function snap(x, y) { // ближайшая вершина этажного подграфа (как в приложении, без радиуса)
    let best = -1, bd = Infinity;
    g.vertices.forEach((v, i) => {
      const d = (v.x - x) * (v.x - x) + (v.y - y) * (v.y - y);
      if (d < bd) { bd = d; best = i; }
    });
    return best < 0 ? -1 : base + best;
  }

  fd.entrances.filter(e => e.kind === 'stairs' && e.usable).forEach(e => {
    const sv = addV(fid, e.x, e.y);
    const near = snap(e.x, e.y);
    if (near >= 0) linkV(sv, near);
    stairVById[e.id] = sv;
    stairs.push({ fid, en: e, v: sv });
  });
  fd.rooms.filter(r => r.number && r.status !== 'nonexistent' && floorForRoom(r.number)).forEach(r => {
    const v = addV(fid, r.x, r.y);
    const n = snap(r.x, r.y);
    if (n >= 0) linkV(v, n);
    roomV[r.number.toLowerCase()] = v;
  });
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

// Псевдо-комната «главный вход» (как mainEntranceRoom в приложении) — для демо
for (const e of (floors['1'].entrances || [])) {
  if (e.kind === 'main') { roomV['100'] = addV('1', e.x, e.y); /* snap ниже не нужен: вход лежит у графа */ }
}
// привязка псевдо-комнаты к графу (как snap в findMultiFloorRoute)
if (roomV['100'] != null) {
  const g1 = buildGraphFloor(floors['1']);
  let best = -1, bd = Infinity;
  const ex = gVerts[roomV['100']].x, ey = gVerts[roomV['100']].y;
  g1.vertices.forEach((v, i) => { const d = (v.x - ex) ** 2 + (v.y - ey) ** 2; if (d < bd) { bd = d; best = i; } });
  if (best >= 0) linkV(roomV['100'], gVerts.findIndex(gv => gv.floor === '1' && Math.abs(gv.x - g1.vertices[best].x) < VDUP && Math.abs(gv.y - g1.vertices[best].y) < VDUP));
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

fs.writeFileSync(REPORT, lines.join('\n') + '\n', 'utf8');
console.log('\nОтчёт записан: ' + REPORT);
