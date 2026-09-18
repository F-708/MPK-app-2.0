// Проверка прижатия к стенам: подкладываем синтетический план со стенами
// и смотрим, находит ли редактор линии и правильно ли считает контур.
const fs = require('fs');
const path = require('path');
const { JSDOM, VirtualConsole } = require('jsdom');

const HTML = fs.readFileSync(path.join(__dirname, '..', 'tools', 'editor.html'), 'utf8');
const vc = new VirtualConsole();
const problems = [];
vc.on('jsdomError', e => problems.push(String(e.message || e)));

const dom = new JSDOM(HTML, {
  runScripts: 'dangerously', pretendToBeVisual: true,
  url: 'https://local.test/', virtualConsole: vc,
});
const w = dom.window;

let passed = 0, failed = 0;
const check = (name, cond, detail) => {
  if (cond) { passed++; console.log('  ок   ' + name); }
  else { failed++; console.log('  ПЛОХО ' + name + (detail ? ' — ' + detail : '')); }
};

// Рисуем «план»: комната с координатами 100..300 по X и 150..400 по Y.
// Стены — линии толщиной 3 пикселя.
const W = 2048, H = 1143;
w.eval(`
  wallMask = new Uint8Array(${W} * ${H});
  function wallV(x, y0, y1) { for (let y = y0; y <= y1; y++) for (let d = -1; d <= 1; d++) wallMask[y * ${W} + x + d] = 1; }
  function wallH(y, x0, x1) { for (let x = x0; x <= x1; x++) for (let d = -1; d <= 1; d++) wallMask[(y + d) * ${W} + x] = 1; }
  wallV(100, 150, 400); wallV(300, 150, 400);
  wallH(150, 100, 300); wallH(400, 100, 300);
`);

console.log('— поиск стен (стена толщиной 3 px, допуск ±3)');
const near = (v, want) => Math.abs(v - want) <= 3;
check('левая стена найдена', near(w.eval('snapCol(90, 200, 350)'), 100),
  'вернулось ' + w.eval('snapCol(90, 200, 350)'));
check('правая стена найдена', near(w.eval('snapCol(310, 200, 350)'), 300),
  'вернулось ' + w.eval('snapCol(310, 200, 350)'));
check('верхняя стена найдена', near(w.eval('snapRow(140, 150, 250)'), 150),
  'вернулось ' + w.eval('snapRow(140, 150, 250)'));
check('нижняя стена найдена', near(w.eval('snapRow(410, 150, 250)'), 400),
  'вернулось ' + w.eval('snapRow(410, 150, 250)'));

console.log('— где стены нет, там ничего не двигаем');
check('без стены рядом значение не меняется',
  w.eval('snapCol(1000, 200, 350)') === 1000,
  'вернулось ' + w.eval('snapCol(1000, 200, 350)'));
check('без стены рядом значение не меняется (строка)',
  w.eval('snapRow(900, 150, 250)') === 900);

console.log('— контур комнаты');
// Рисуем «на глаз» внутри комнаты — контур должен раздвинуться до стен
const c = w.eval('autoContour(130, 180, 270, 370)');
check('левая грань прижалась к 100', near(Math.round(c.x0), 100), 'получилось ' + Math.round(c.x0));
check('правая грань прижалась к 300', near(Math.round(c.x1), 300), 'получилось ' + Math.round(c.x1));
check('верхняя грань прижалась к 150', near(Math.round(c.y0), 150), 'получилось ' + Math.round(c.y0));
check('нижняя грань прижалась к 400', near(Math.round(c.y1), 400), 'получилось ' + Math.round(c.y1));
check('рамка раздвинулась с 140x190 до размеров комнаты',
  (c.x1 - c.x0) > 190 && (c.y1 - c.y0) > 240,
  'получилось ' + Math.round(c.x1 - c.x0) + 'x' + Math.round(c.y1 - c.y0));

console.log('— выключенный автоконтур ничего не трогает');
w.eval("document.getElementById('autoContour').checked = false; document.getElementById('alignNeighbors').checked = false;");
const c2 = w.eval('autoContour(130, 180, 270, 370)');
check('с выключенным автоконтуром рамка не меняется',
  c2.x0 === 130 && c2.y0 === 180 && c2.x1 === 270 && c2.y1 === 370,
  JSON.stringify(c2));

console.log('— расширение стен для поиска комнат');
w.eval('wallMask = new Uint8Array(2048*1143); wallMask[500*2048 + 500] = 1;');
const d = w.eval('dilateMask(wallMask, 3)');
let count = 0;
for (let i = 0; i < d.length; i++) if (d[i]) count++;
check('расширение раздуло точку до квадрата 7x7 (49)', count === 49, 'получилось ' + count);

setTimeout(() => {
  check('ошибок не появилось', problems.length === 0, problems.join(' | '));
  console.log('');
  console.log('ИТОГО: пройдено ' + passed + ', провалено ' + failed);
  process.exit(failed ? 1 : 0);
}, 100);
