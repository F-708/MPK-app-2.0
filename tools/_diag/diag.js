// Диагностика map-editor.html: исполняем основной <script> страницы ВМЕСТЕ с
// тестовым кодом (в одном скоупе), эмулируя DOM/canvas/localStorage/Image.
// W5: window-шим научился хранить слушателей (mouseup/keydown) — тестируем
// drag-создание объектов и Esc. Функциональные тесты (а)-(д).
const fs = require('fs');
const path = require('path');

const html = fs.readFileSync(path.join(__dirname, '..', 'map-editor.html'), 'utf8');
const all = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)];
const code = all[all.length - 1][1]; // основной скрипт — последний

let failed = false;
const check = (name, cond, extra) => {
  if (cond) console.log('PASS ' + name + (extra ? ' | ' + extra : ''));
  else { failed = true; console.log('FAIL ' + name + (extra ? ' | ' + extra : '')); }
};

const TEST = `
;(function(){
  try {
    const fire = (type, x, y) => {
      const ev = { type, clientX:x, clientY:y, button:0, buttons:1, offsetX:x, offsetY:y,
        preventDefault(){}, stopPropagation(){}, ctrlKey:false, shiftKey:false, altKey:false, metaKey:false,
        key:'', detail:1, deltaY:0, deltaX:0, wheelDelta:0, target: cv, currentTarget: cv, dataTransfer:null };
      (cv._listeners[type]||[]).forEach(fn => fn(ev));
    };
    const fireWin = (type, extra) => {
      const ev = Object.assign({ type, preventDefault(){}, stopPropagation(){},
        target:null, ctrlKey:false, shiftKey:false, metaKey:false, key:'' }, extra||{});
      (window._listeners[type]||[]).forEach(fn => fn(ev));
    };
    const sx = x => Math.round(x*W*cam.s + cam.ox);
    const sy = y => Math.round(y*H*cam.s + cam.oy);
    const fd1 = map.floors['1'];

    // (а) init
    check('init-rooms-floor1', fd1 && fd1.rooms.length > 0, 'rooms=' + (fd1&&fd1.rooms.length));
    check('init-floor2-graph', map.floors['2'].corridors.length > 0 && map.floors['2'].nodes.length > 0,
      'corridors=' + map.floors['2'].corridors.length + ' nodes=' + map.floors['2'].nodes.length);
    check('init-bg2', !!(bg['2']&&bg['2'].ready));
    check('init-rectCorridors-schema', Array.isArray(fd1.rectCorridors));

    // (б) клик по комнате -> выбор
    const r0 = fd1.rooms[0];
    fire('mousedown', sx(r0.x), sy(r0.y));
    check('click-select-room', !!(sel && sel.kind==='room' && sel.obj===r0), sel ? sel.kind : 'null');
    fireWin('mouseup');

    // (в) дверь одним кликом
    tool = 'door';
    const r1 = fd1.rooms[1];
    fire('mousedown', sx(r1.x - r1.w*0.3), sy(r1.y - r1.h*0.3));
    fireWin('mouseup');
    check('door-one-click', Array.isArray(r1.door) && r1.door.length===2, JSON.stringify(r1.door));
    tool = 'select';

    // (г) прямоугольник-коридор drag'ом -> экспорт
    // (встроенная карта уже содержит rectCorridors — проверяем ДЕЛЬТУ относительно «до»)
    tool = 'corridor'; corridorMode = 'rect';
    const corrBefore = fd1.corridors.length;
    const rectBefore = fd1.rectCorridors.length;
    fire('mousedown', sx(0.10), sy(0.60));
    fire('mousemove', sx(0.45), sy(0.64));
    fireWin('mouseup');
    check('rect-corridor-created', (fd1.rectCorridors||[]).length === rectBefore + 1 && fd1.corridors.length === corrBefore,
      'rectCorridors=' + (fd1.rectCorridors||[]).length + ' (было ' + rectBefore + ') corridors(mem)=' + fd1.corridors.length);
    const rc = fd1.rectCorridors[fd1.rectCorridors.length - 1];
    check('rect-corridor-normalized', Math.abs(rc.x-0.275)<0.002 && Math.abs(rc.y-0.62)<0.002 && Math.abs(rc.w-0.35)<0.002,
      'x='+rc.x+' y='+rc.y+' w='+rc.w+' h='+rc.h); // 0.002 = погрешность пиксельного roundtrip экран↔мир
    const out = JSON.parse(serialize());
    const f1out = out.floors['1'];
    const conv = f1out.corridors.find(c => c.id === rc.id);
    check('export-rect-kept-in-json', (f1out.rectCorridors||[]).length === rectBefore + 1);
    check('export-corridor-added', f1out.corridors.length === corrBefore + rectBefore + 1, 'corridors=' + f1out.corridors.length + ' (реальных ' + corrBefore + ' + прямоуг. ' + (rectBefore+1) + ')');
    check('export-conv-2points', !!conv && conv.points.length === 2, conv ? JSON.stringify(conv) : 'нет конверсии');
    // горизонтальный прямоугольник: осевая по центру вдоль длинной стороны, width = короткая
    const okAxis = conv && Math.abs(conv.points[0][1]-rc.y)<2e-4 && Math.abs(conv.points[1][1]-rc.y)<2e-4
      && Math.abs(conv.points[0][0]-(rc.x-rc.w/2))<2e-4 && Math.abs(conv.points[1][0]-(rc.x+rc.w/2))<2e-4;
    const okWidth = conv && Math.abs(conv.width - Math.round(rc.h*H/W*10000)/10000) < 1e-9;
    check('export-conv-axis-long-side', !!okAxis);
    check('export-conv-width-short-side', !!okWidth, conv ? 'width='+conv.width+' (h*H/W='+(rc.h*H/W).toFixed(4)+')' : '');
    const out2 = JSON.parse(serialize());
    check('export-idempotent', out2.floors['1'].corridors.length === f1out.corridors.length);
    tool = 'select'; corridorMode = 'line';

    // (е) снап точки коридора: тащим точку к узлу -> прилипает точно
    curFloor = '2'; draw();
    const f2 = map.floors['2'];
    // точка без маркеров рядом: cor_v210322[2] = (0.196,0.267), тащим её к узлу n_st238 (0.29,0.311)
    const cSnap = f2.corridors.find(c => c.id === 'cor_v210322');
    const nT = f2.nodes.find(n => n.id === 'n_st238');
    const pStart = cSnap.points[2];
    const h0 = hitTest({x:pStart[0], y:pStart[1]});
    check('snap-hit-corridor-point', !!(h0 && h0.kind==='corridor' && h0.obj===cSnap && h0.pi===2),
      h0 ? h0.kind+':'+(h0.pi!=null?h0.pi:'') : 'null');
    fire('mousedown', sx(pStart[0]), sy(pStart[1]));
    fire('mousemove', sx(nT.x+0.002), sy(nT.y));
    const snapped = cSnap.points[1];
    const isNode = f2.nodes.some(n => Math.abs(n.x-snapped[0])<1e-6 && Math.abs(n.y-snapped[1])<1e-6);
    const isDoor = f2.rooms.some(r => r.door && Math.abs(r.door[0]-snapped[0])<1e-6 && Math.abs(r.door[1]-snapped[1])<1e-6);
    check('snap-point-exact', isNode || isDoor, 'point=' + JSON.stringify(snapped) + (isNode?' (node)':' (door)') + ' magnet=' + !!snapMark);
    fireWin('mouseup');

    // (д) превью маршрута A->B на этаже 2 (встроенная карта с коридорами)
    tool = 'route';
    const ra = f2.rooms.find(r => r.number === '201-1');
    const rb = f2.rooms.find(r => r.number === '223'); // дальняя комната с дверью
    check('route-setup', !!ra && !!rb && !!ra.door && !!rb.door, 'A=' + (ra&&ra.number) + ' B=' + (rb&&rb.number));
    fire('mousedown', sx(ra.x), sy(ra.y));
    check('route-A-from-door', !!routeA && Math.abs(routeA.x-ra.door[0])<1e-9 && Math.abs(routeA.y-ra.door[1])<1e-9,
      'routeA=' + JSON.stringify(routeA));
    fire('mousedown', sx(rb.x), sy(rb.y));
    check('route-path-nonempty', !!(routePreview && routePreview.points.length >= 5),
      'points=' + (routePreview ? routePreview.points.length : 0) +
      (routePreview ? ' | путь: ' + routePreview.points.map(p=>'(' + p[0].toFixed(3)+',' + p[1].toFixed(3) + ')').join(' ') : ''));
    check('route-B-from-door', !!(routePreview && routePreview.points.length &&
      Math.abs(routePreview.points[routePreview.points.length-1][0]-rb.door[0])<1e-9 &&
      Math.abs(routePreview.points[routePreview.points.length-1][1]-rb.door[1])<1e-9));
    const outR = JSON.parse(serialize());
    check('route-not-saved', !('routePreview' in outR) && !(outR.floors['2'].routes||[]).length);
    fireWin('keydown', { key:'Escape' });
    check('route-esc-clears', routePreview===null && routeA===null);
    tool = 'select';

    // (ж) реимпорт экспорта: производные corridors сняты, rect — источник истины;
    // после переноса прямоугольника повторный экспорт даёт НОВУЮ осевую
    loadMapText(JSON.stringify(out), 'export-test');
    check('reimport-strips-derived', map.floors['1'].corridors.length === corrBefore && map.floors['1'].rectCorridors.length === rectBefore + 1,
      'corridors=' + map.floors['1'].corridors.length + ' (реальных ' + corrBefore + ') rectCorridors=' + map.floors['1'].rectCorridors.length);
    const rcM = map.floors['1'].rectCorridors[map.floors['1'].rectCorridors.length - 1];
    rcM.x = 0.4; // двинули прямоугольник
    const out3 = JSON.parse(serialize());
    const conv3 = out3.floors['1'].corridors.find(c => c.id === rcM.id);
    const rc3 = out3.floors['1'].rectCorridors.find(r => r.id === rcM.id);
    check('reexport-after-move-updated', !!conv3 && out3.floors['1'].corridors.length === corrBefore + rectBefore + 1 &&
      Math.abs(conv3.points[0][0]-(rc3.x-rc3.w/2))<2e-4 && Math.abs(conv3.points[1][0]-(rc3.x+rc3.w/2))<2e-4,
      conv3 ? 'ось=' + JSON.stringify(conv3.points) : 'нет');

    // (з) W5-фикс: отрисовка rect-коридора — заливка+рамка, НИКАКИХ lineWidth ~ width*W.
    // Пустой этаж с одним выбранным rect-коридором: все lineWidth <= 3/cam.s.
    map.floors['9'] = emptyFloor();
    map.floors['9'].rectCorridors.push({id:'rcc_test', x:0.5, y:0.5, w:0.2, h:0.08});
    sel = {kind:'rectCorridor', obj: map.floors['9'].rectCorridors[0]};
    curFloor = '9';
    __lwSets.length = 0;
    draw();
    sel = null; curFloor = '1';
    const maxLw = __lwSets.length ? Math.max.apply(null, __lwSets) : 0;
    check('rect-draw-no-fat-stroke', __lwSets.length > 0 && maxLw <= 3/cam.s,
      'max lineWidth=' + maxLw + ' (порог 3/cam.s=' + (3/cam.s).toFixed(3) + ', записей ' + __lwSets.length + ')');
    delete map.floors['9'];

    // (и) W6: связи лестниц между этажами — UI-функции, сериализация, импорт, снятие
    curFloor = '1'; tool = 'select';
    const stA = {id:'e_tA', x:0.40, y:0.40, kind:'stairs', dir:90, title:'', usable:true, links:[]};
    const stB = {id:'e_tB', x:0.42, y:0.42, kind:'stairs', dir:90, title:'', usable:true, nearRoom:'238', links:[]};
    map.floors['1'].entrances.push(stA);
    map.floors['2'].entrances.push(stB);
    sel = {kind:'entrance', obj:stA};
    renderPanel();                       // панель лестницы со списком связей не падает
    openLinkDialog(stA); closeLinkDialog(); // мини-диалог выбора этажа не падает
    startStairPick(stA, '2');            // = нажатие кнопки этажа в диалоге
    check('stairs-pick-floor', curFloor==='2' && !!stairLinkPick && stairLinkPick.en===stA,
      'curFloor='+curFloor+' pick='+(stairLinkPick?stairLinkPick.en.id:'null'));
    fire('mousedown', sx(stB.x), sy(stB.y)); // клик по лестнице B на этаже 2
    check('stairs-link-created', stA.links.length===1 && stA.links[0]==='e_tB' && stB.links.length===1 && stB.links[0]==='e_tA',
      'A→'+JSON.stringify(stA.links)+' B→'+JSON.stringify(stB.links));
    check('stairs-link-return', curFloor==='1' && !!sel && sel.obj===stA, 'curFloor='+curFloor);
    renderPanel(); draw();               // панель со списком связей + отрисовка подковы/бейджа
    const fB = findEntranceById('e_tB');
    check('stairs-find-entrance', !!fB && fB.floor==='2' && fB.en===stB);
    // сериализация сохраняет links у обеих
    const outS = JSON.parse(serialize());
    const aS = outS.floors['1'].entrances.find(e=>e.id==='e_tA');
    const bS = outS.floors['2'].entrances.find(e=>e.id==='e_tB');
    check('stairs-links-serialized', !!aS && !!bS && JSON.stringify(aS.links)===JSON.stringify(['e_tB']) && JSON.stringify(bS.links)===JSON.stringify(['e_tA']),
      'A='+JSON.stringify(aS&&aS.links)+' B='+JSON.stringify(bS&&bS.links));
    // links не появляется у комнат/коридоров — приложение их полей не ждёт
    check('stairs-links-only-entrances', !outS.floors['1'].rooms.some(r=>r.links!=null) && !outS.floors['1'].corridors.some(c=>c.links!=null));
    // импорт читает
    loadMapText(JSON.stringify(outS), 'stairs-links-test');
    const aI = map.floors['1'].entrances.find(e=>e.id==='e_tA');
    const bI = map.floors['2'].entrances.find(e=>e.id==='e_tB');
    check('stairs-links-imported', !!aI && !!bI && aI.links.length===1 && aI.links[0]==='e_tB' && bI.links[0]==='e_tA');
    // снятие связи = обработчик ✕ в панели
    removeStairLink(aI, 'e_tB');
    check('stairs-link-removed', aI.links.length===0 && bI.links.length===0,
      'A='+JSON.stringify(aI.links)+' B='+JSON.stringify(bI.links));

    // (к) W6: подписи-инструкции — HINTS/TOOLTIPS на месте, панель/статус-бар не падают
    check('hints-tools-complete', ['select','room','corridor','node','marker','door','route'].every(t=>typeof HINTS[t]==='string' && HINTS[t].length>20));
    check('tooltips-tools-complete', ['select','room','corridor','node','marker','door','route'].every(t=>Array.isArray(TOOLTIPS[t]) && TOOLTIPS[t].length>=2));
    setHint(); renderPanel(); draw();

    // (л) W6: узлы — панель со списком связей, снятие связи ✕, подсветка не падает
    const f2n = map.floors['2'];
    const nL = f2n.nodes.find(n=>(n.links||[]).length>0);
    if(nL){
      curFloor = '2';
      sel = {kind:'node', obj:nL};
      renderPanel(); draw();             // панель со списком связей узла + подсветка
      const otherId = nL.links[0];
      const other = f2n.nodes.find(n=>n.id===otherId);
      removeNodeLink(nL, otherId);       // = ✕ в панели
      check('node-link-removed', (nL.links||[]).indexOf(otherId)<0 && (other.links||[]).indexOf(nL.id)<0,
        'A='+JSON.stringify(nL.links)+' B='+JSON.stringify(other.links));
    } else check('node-link-removed', false, 'нет узлов со связями');

    // (м) W6: снап узла при перетаскивании — тащим узел к двери кабинета, прилипает точно
    const f2q = map.floors['2'];
    const roomD = f2q.rooms.find(r=>r.door && f2q.nodes.every(n=>Math.hypot((r.door[0]-n.x)*W,(r.door[1]-n.y)*H) > 20));
    const nDrag = f2q.nodes.find(n=> roomD && Math.hypot((n.x-roomD.door[0])*W,(n.y-roomD.door[1])*H) > 60
      && f2q.entrances.every(e=>Math.hypot((n.x-e.x)*W,(n.y-e.y)*H) > 15)
      && f2q.nodes.every(m=>m===n || Math.hypot((m.x-n.x)*W,(m.y-n.y)*H) > 15));
    if(roomD && nDrag){
      curFloor = '2'; sel = {kind:'node', obj:nDrag};
      fire('mousedown', sx(nDrag.x), sy(nDrag.y));
      fire('mousemove', sx(roomD.door[0]), sy(roomD.door[1]));
      const snappedN = [nDrag.x, nDrag.y];
      check('node-snap-to-door', Math.abs(snappedN[0]-roomD.door[0])<1e-9 && Math.abs(snappedN[1]-roomD.door[1])<1e-9,
        'node=('+snappedN[0]+','+snappedN[1]+') door='+JSON.stringify(roomD.door)+' magnet='+!!snapMark);
      fireWin('mouseup');
    } else check('node-snap-to-door', false, 'нет подходящей пары узел↔дверь');

    console.log('INTERACTION OK');
  } catch (e) {
    console.error('INTERACT ERROR:', e && e.stack || e);
    global.__testFailed = true;
  }
})();
`;

// — DOM шим —
function makeEl() {
  const el = {
    style: {}, dataset: {}, _listeners: {},
    clientWidth: 1200, clientHeight: 800, width: 0, height: 0,
    value: '', textContent: '', innerHTML: '',
    classList: { add(){}, remove(){}, toggle(){}, contains(){ return false; } },
    getContext() { return ctxStub; },
    addEventListener(t, fn) { (this._listeners[t] ||= []).push(fn); },
    removeEventListener() {},
    setAttribute() {}, getAttribute() { return null; },
    getBoundingClientRect() { return { left: 0, top: 0, width: 1200, height: 800, right: 1200, bottom: 800 }; },
    querySelector() { return makeEl(); }, querySelectorAll() { return []; },
    appendChild(c) { return c; }, focus(){}, blur(){},
  };
  return el;
}
const __lwSets = global.__lwSets = [];
const ctxStub = new Proxy({}, {
  get(_, k) {
    if (k === 'canvas') return {};
    return (...a) => {
      if (k === 'measureText') return { width: 42 };
      if (k === 'createLinearGradient' || k === 'createRadialGradient') return { addColorStop(){} };
      return undefined;
    };
  },
  set(_, k, v) { if (k === 'lineWidth') __lwSets.push(v); return true; },
});
const elements = {};
global.document = {
  getElementById(id) { return elements[id] ||= makeEl(); },
  querySelector() { return makeEl(); }, querySelectorAll() { return []; },
  createElement() { return makeEl(); }, addEventListener() {},
  body: makeEl(), documentElement: makeEl(),
};
global.window = {
  _listeners: {},
  addEventListener(t, fn) { (this._listeners[t] ||= []).push(fn); },
  removeEventListener() {},
  devicePixelRatio: 1,
  innerWidth: 1200, innerHeight: 800,
  location: { href: 'file:///map-editor.html' },
};
global.localStorage = { _s: {}, getItem(k){ return this._s[k] ?? null; }, setItem(k,v){ this._s[k]=String(v); }, removeItem(k){ delete this._s[k]; } };
global.Image = class { set src(v) { if (this.onload) this.onload(); } };
global.URL = { createObjectURL: () => 'blob:x', revokeObjectURL(){} };
global.Blob = class {};
global.requestAnimationFrame = (fn) => {};
global.ResizeObserver = class { constructor(cb){ if (cb) cb(); } observe(){} unobserve(){} disconnect(){} };
global.getComputedStyle = () => ({ getPropertyValue: () => '0' });

try {
  const fn = new Function('check', code + TEST);
  fn(check);
  console.log('INIT OK');
} catch (e) {
  failed = true;
  console.error('RUNTIME ERROR при старте:');
  console.error(e && e.stack || e);
}
if (global.__testFailed) failed = true;
process.exit(failed ? 2 : 0);
