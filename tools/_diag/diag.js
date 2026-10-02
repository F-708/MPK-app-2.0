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
    tool = 'corridor'; corridorMode = 'rect';
    const corrBefore = fd1.corridors.length;
    fire('mousedown', sx(0.10), sy(0.60));
    fire('mousemove', sx(0.45), sy(0.64));
    fireWin('mouseup');
    check('rect-corridor-created', (fd1.rectCorridors||[]).length === 1 && fd1.corridors.length === corrBefore,
      'rectCorridors=' + (fd1.rectCorridors||[]).length + ' corridors(mem)=' + fd1.corridors.length);
    const rc = fd1.rectCorridors[0];
    check('rect-corridor-normalized', Math.abs(rc.x-0.275)<0.002 && Math.abs(rc.y-0.62)<0.002 && Math.abs(rc.w-0.35)<0.002,
      'x='+rc.x+' y='+rc.y+' w='+rc.w+' h='+rc.h); // 0.002 = погрешность пиксельного roundtrip экран↔мир
    const out = JSON.parse(serialize());
    const f1out = out.floors['1'];
    const conv = f1out.corridors.find(c => String(c.id).indexOf('rcc_')===0);
    check('export-rect-kept-in-json', (f1out.rectCorridors||[]).length === 1);
    check('export-corridor-added', f1out.corridors.length === corrBefore + 1, 'corridors=' + f1out.corridors.length + ' (before ' + corrBefore + ')');
    check('export-conv-2points', !!conv && conv.points.length === 2, conv ? JSON.stringify(conv) : 'no rcc_*');
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
    // точка без маркеров рядом: cor_v14b754[1] = (0.29,0.27), тащим её к узлу n_st238 (0.29,0.311)
    const cSnap = f2.corridors.find(c => c.id === 'cor_v14b754');
    const nT = f2.nodes.find(n => n.id === 'n_st238');
    const pStart = cSnap.points[1];
    const h0 = hitTest({x:pStart[0], y:pStart[1]});
    check('snap-hit-corridor-point', !!(h0 && h0.kind==='corridor' && h0.obj===cSnap && h0.pi===1),
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
    check('reimport-strips-derived', map.floors['1'].corridors.length === 0 && map.floors['1'].rectCorridors.length === 1,
      'corridors=' + map.floors['1'].corridors.length + ' rectCorridors=' + map.floors['1'].rectCorridors.length);
    map.floors['1'].rectCorridors[0].x = 0.4; // двинули прямоугольник
    const out3 = JSON.parse(serialize());
    const conv3 = out3.floors['1'].corridors.find(c => String(c.id).indexOf('rcc_')===0);
    const rc3 = out3.floors['1'].rectCorridors[0];
    check('reexport-after-move-updated', !!conv3 && out3.floors['1'].corridors.length===1 &&
      Math.abs(conv3.points[0][0]-(rc3.x-rc3.w/2))<2e-4 && Math.abs(conv3.points[1][0]-(rc3.x+rc3.w/2))<2e-4,
      conv3 ? 'ось=' + JSON.stringify(conv3.points) : 'нет');
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
const ctxStub = new Proxy({}, {
  get(_, k) {
    if (k === 'canvas') return {};
    return (...a) => {
      if (k === 'measureText') return { width: 42 };
      if (k === 'createLinearGradient' || k === 'createRadialGradient') return { addColorStop(){} };
      return undefined;
    };
  },
  set() { return true; },
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
