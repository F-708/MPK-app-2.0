// Диагностика map-editor.html: исполняем основной <script> страницы ВМЕСТЕ с
// тестовым кодом (в одном скоупе), эмулируя DOM/canvas/localStorage/Image.
const fs = require('fs');
const path = require('path');

const html = fs.readFileSync(path.join(__dirname, '..', 'map-editor.html'), 'utf8');
const all = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)];
const code = all[all.length - 1][1]; // основной скрипт — последний

const TEST = `
;(function(){
  try {
    const rooms = map.floors['1'] ? map.floors['1'].rooms.length : -1;
    console.log('rooms floor1:', rooms, '| cam:', JSON.stringify(cam), '| bg1:', !!(bg['1']&&bg['1'].ready));
    const fire = (type, x, y) => {
      const ev = { type, clientX:x, clientY:y, button:0, buttons:1, offsetX:x, offsetY:y,
        preventDefault(){}, stopPropagation(){}, ctrlKey:false, shiftKey:false, altKey:false, metaKey:false,
        key:'', detail:1, deltaY:0, deltaX:0, wheelDelta:0, target: cv, currentTarget: cv, dataTransfer:null };
      (cv._listeners[type]||[]).forEach(fn => fn(ev));
    };
    const fd = map.floors['1'];
    const r0 = fd && fd.rooms[0];
    if (r0) {
      const sx = Math.round(r0.x*W*cam.s + cam.ox);
      const sy = Math.round(r0.y*H*cam.s + cam.oy);
      console.log('кликаю в комнату', r0.number, 'экран:', sx, sy);
      fire('mousedown', sx, sy);
      fire('mouseup', sx, sy);
      console.log('ВЫБРАНО:', sel ? (sel.kind + ' ' + (sel.obj.number || sel.obj.id)) : 'ПУСТО');
    } else {
      console.log('НЕТ КОМНАТ — карта не загрузилась!');
    }
    fire('mousemove', 500, 300);
    // W4e: тест инструмента «Двери» — клик внутри комнаты ставит дверь на ближайший край
    tool = 'door';
    const r1 = fd.rooms[1];
    const cx = r1.x + r1.w*0.2, cy = r1.y + r1.h*0.2; // внутри, ближе к левому краю
    const sxc = Math.round(cx*W*cam.s + cam.ox), syc = Math.round(cy*H*cam.s + cam.oy);
    fire('mousedown', sxc, syc); fire('mouseup', sxc, syc);
    console.log('дверь после клика:', JSON.stringify(r1.door), '(ожидаю x = левый край', (r1.x - r1.w/2).toFixed(4), ')');
    tool = 'select';
    console.log('INTERACTION OK');
  } catch (e) {
    console.error('INTERACT ERROR:', e && e.stack || e);
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
  addEventListener() {}, devicePixelRatio: 1,
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

let failed = false;
try {
  const fn = new Function(code + TEST);
  fn.call(global.window);
  console.log('INIT OK');
} catch (e) {
  failed = true;
  console.error('RUNTIME ERROR при старте:');
  console.error(e && e.stack || e);
}
process.exit(failed ? 2 : 0);
