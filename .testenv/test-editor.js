// Проверка редактора: страница запускается по-настоящему, в jsdom.
// Ловим ошибки при загрузке и прогоняем основные действия.
const fs = require('fs');
const path = require('path');
const { JSDOM, VirtualConsole } = require('jsdom');

const HTML = fs.readFileSync(path.join(__dirname, '..', 'tools', 'editor.html'), 'utf8');

const problems = [];
const vc = new VirtualConsole();
vc.on('jsdomError', e => problems.push('jsdomError: ' + (e.message || e)));
vc.on('error', (...a) => problems.push('console.error: ' + a.join(' ')));

const dom = new JSDOM(HTML, {
  runScripts: 'dangerously',
  pretendToBeVisual: true,
  url: 'https://local.test/',
  virtualConsole: vc,
});

const w = dom.window;
const doc = w.document;

let passed = 0, failed = 0;
function check(name, cond, detail) {
  if (cond) { passed++; console.log('  ок   ' + name); }
  else { failed++; console.log('  ПЛОХО ' + name + (detail ? ' — ' + detail : '')); }
}

console.log('— загрузка страницы');
check('нет ошибок при запуске', problems.length === 0, problems.join(' | '));

// Доступ к внутреннему состоянию скрипта
let S = null;
try { S = w.eval('S'); } catch (e) { /* ниже сообщим */ }
check('состояние редактора доступно', !!S);

console.log('— интерфейс собран');
check('кнопок этажей: 3', doc.querySelectorAll('#floors .btn').length === 3,
  'найдено ' + doc.querySelectorAll('#floors .btn').length);
check('инструментов: 12', doc.querySelectorAll('#tools .btn').length === 12,
  'найдено ' + doc.querySelectorAll('#tools .btn').length);
check('слоёв: 8', doc.querySelectorAll('#layers input').length === 8,
  'найдено ' + doc.querySelectorAll('#layers input').length);
check('планов встроено: 3', doc.querySelectorAll('.planWrap').length === 3,
  'найдено ' + doc.querySelectorAll('.planWrap').length);
check('у каждого плана есть svg', doc.querySelectorAll('.planWrap svg').length === 3,
  'найдено ' + doc.querySelectorAll('.planWrap svg').length);
check('подсказка заполнена', (doc.getElementById('hint').textContent || '').length > 10);
check('viewBox оверлея задан',
  (doc.getElementById('overlay').getAttribute('viewBox') || '').includes('2048'));

console.log('— переключение этажей');
const floors = doc.querySelectorAll('#floors .btn');
floors[1].click();
check('после клика активен 2 этаж', S.floor === '2', 'получилось ' + S.floor);
check('виден ровно один план',
  doc.querySelectorAll('.planWrap.on').length === 1);
floors[2].click();
check('3 и 4 этажи', S.floor === '3-4');

console.log('— загрузка rooms.json');
const roomsPath = path.join(__dirname, '..', 'tools', 'rooms.json');
if (fs.existsSync(roomsPath)) {
  const before = S.data['1'].rooms.length;
  // вызываем импорт напрямую тем же путём, что и кнопка
  const text = fs.readFileSync(roomsPath, 'utf8');
  w.eval('(function(t){ importRooms(t); })')(text);
  const total = ['1', '2', '3-4'].reduce((n, f) => n + S.data[f].rooms.length, 0);
  check('кабинеты загрузились', total === 188, 'получилось ' + total);
} else {
  check('rooms.json найден', false, 'файла нет');
}

console.log('— операции с разметкой');
try {
  doc.querySelectorAll('#floors .btn')[0].click();   // возвращаемся на 1 этаж
  S.data['1'].nodes.push({ id: 'n_test', x: 0.5, y: 0.5, links: [] });
  S.data['1'].corridors.push({ id: 'c_test', points: [[0.1, 0.1], [0.2, 0.2]], width: 0.012 });
  S.data['1'].entrances.push({ id: 'e_test', x: 0.3, y: 0.3, kind: 'main', title: 'Главный вход' });
  w.eval('render()');
  const svg = doc.getElementById('overlay').innerHTML;
  check('коридор нарисован', svg.includes('polyline'));
  check('узел нарисован', svg.includes('circle'));
  check('вход нарисован', svg.includes('polygon'));
  check('кабинеты нарисованы все 61 (1 этаж)',
    (svg.match(/data-type="room"/g) || []).length === S.data['1'].rooms.length,
    'найдено ' + (svg.match(/data-type="room"/g) || []).length + ' при ' + S.data['1'].rooms.length + ' в данных');
} catch (e) {
  check('рисование объектов', false, e.message);
}

console.log('— авторазметка (без растеризации плана)');
try {
  const r = w.eval('autoContour(100, 100, 200, 200)');
  check('autoContour возвращает рамку',
    r && typeof r.x0 === 'number' && r.x1 > r.x0,
    JSON.stringify(r));
} catch (e) {
  check('autoContour работает', false, e.message);
}

console.log('— экспорт');
try {
  const json = w.eval('JSON.stringify({version:1, scaleMeters:S.scaleMeters, floors:S.data})');
  const o = JSON.parse(json);
  check('экспорт содержит floors', !!o.floors);
  check('в экспорте 3 этажа', Object.keys(o.floors).length === 3);
  check('кабинеты в экспорте есть',
    ['1', '2', '3-4'].reduce((n, f) => n + o.floors[f].rooms.length, 0) === 188);
} catch (e) {
  check('экспорт JSON', false, e.message);
}

console.log('— сохранение в браузере');
try {
  w.eval('saveLocal()');
  const saved = w.localStorage.getItem('mpk-map-editor-v3');
  check('данные записались в localStorage', !!saved && saved.length > 100);
} catch (e) {
  check('saveLocal', false, e.message);
}

console.log('— поиск кабинетов (замкнутые области)');
try {
  const n0 = S.data['1'].rooms.length;
  w.eval('wallMask = new Uint8Array(2048*1143); detectRooms();');
  setTimeout(() => {
    // без настоящей растеризации областей не будет — главное, что не падает
    check('detectRooms не падает', true);
  }, 50);
} catch (e) {
  check('detectRooms не падает', false, e.message);
}

setTimeout(() => {
  check('ошибок за всё время не появилось', problems.length === 0, problems.join(' | '));
  console.log('');
  console.log('ИТОГО: пройдено ' + passed + ', провалено ' + failed);
  process.exit(failed ? 1 : 0);
}, 200);
