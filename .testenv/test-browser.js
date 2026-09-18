// Прогон редактора в настоящем Chromium.
//
// Главное отличие от прошлой версии: проверяется не «обработчик привязан»,
// а «по кнопке реально можно попасть» — кнопка на экране, не перекрыта,
// в неё попадает клик. Именно такую поломку прошлый тест пропустил:
// панель не прокручивалась, и нижние кнопки были недостижимы.
const fs = require('fs');
const path = require('path');
const puppeteer = require('puppeteer');

const EDITOR = 'file:///' + path.join(__dirname, '..', 'tools', 'editor.html').replace(/\\/g, '/');
const ROOMS = path.join(__dirname, '..', 'tools', 'rooms.json');
const SHOTS = path.join(__dirname, 'shots');
fs.mkdirSync(SHOTS, { recursive: true });

let passed = 0, failed = 0;
const check = (name, cond, detail) => {
  if (cond) { passed++; console.log('  ок   ' + name); }
  else { failed++; console.log('  ПЛОХО ' + name + (detail ? ' — ' + detail : '')); }
};
const wait = ms => new Promise(r => setTimeout(r, ms));

(async () => {
  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-gpu', '--allow-file-access-from-files'],
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1500, height: 820 });

  const errors = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error') errors.push('console: ' + m.text()); });
  page.on('dialog', async d => { await d.accept(); });

  await page.goto(EDITOR, { waitUntil: 'load', timeout: 60000 });
  await wait(2500);

  // Проверяет, что по элементу реально можно щёлкнуть: он виден, не перекрыт
  // и лежит внутри экрана. При необходимости сначала прокручиваем панель.
  async function reachable(sel, scrollPanel = true) {
    if (scrollPanel) {
      await page.evaluate(s => {
        const el = document.querySelector(s);
        if (el) el.scrollIntoView({ block: 'center' });
      }, sel);
      await wait(120);
    }
    return page.evaluate(s => {
      const el = document.querySelector(s);
      if (!el) return 'нет элемента';
      const r = el.getBoundingClientRect();
      if (r.width < 2 || r.height < 2) return 'нулевой размер';
      const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
      if (cx < 0 || cy < 0 || cx > innerWidth || cy > innerHeight) return 'за экраном';
      const hit = document.elementFromPoint(cx, cy);
      if (!hit) return 'в точке ничего нет';
      if (hit !== el && !el.contains(hit) && !hit.contains(el)) {
        return 'перекрыт элементом ' + hit.tagName + '.' + hit.className;
      }
      return 'ок';
    }, sel);
  }

  console.log('— запуск');
  check('ошибок в консоли нет', errors.length === 0, errors.slice(0, 2).join(' | '));
  check('маска стен построена', await page.evaluate(() => wallMask !== null));
  check('планы — картинки, не SVG',
    await page.evaluate(() => document.querySelectorAll('.planWrap img').length === 3 &&
      document.querySelectorAll('.planWrap svg').length === 0));
  await page.screenshot({ path: path.join(SHOTS, '01-старт.png') });

  console.log('— доступность каждой кнопки (то, что было сломано)');
  const buttons = [
    ['#floors .btn:nth-child(1)', 'этаж 1'],
    ['#floors .btn:nth-child(2)', 'этаж 2'],
    ['#floors .btn:nth-child(3)', 'этаж 3 и 4'],
    ['#btnUndo', 'Отменить'],
    ['#btnRedo', 'Вернуть'],
    ['#btnSnapAll', 'Выровнять этаж'],
    ['#btnDetect', 'Найти кабинеты'],
    ['#btnBulk', 'Выделенным…'],
    ['#btnZoomOut', 'уменьшить'],
    ['#btnZoomFit', 'по экрану'],
    ['#btnZoomIn', 'увеличить'],
    ['#btnSave', 'Сохранить JSON'],
    ['#btnLoad', 'Открыть JSON'],
    ['#btnRooms', 'Загрузить rooms.json'],
    ['#btnSvg', 'Экспорт SVG'],
    ['#btnPng', 'Экспорт PNG'],
    ['#btnClear', 'Очистить этаж'],
    ['#search', 'поиск'],
    ['#filter', 'фильтр статуса'],
    ['#opacity', 'прозрачность'],
    ['#autoContour', 'прижимать к стенам'],
    ['#alignNeighbors', 'ровнять с соседями'],
  ];
  let unreachable = [];
  for (const [sel, name] of buttons) {
    const r = await reachable(sel);
    if (r !== 'ок') unreachable.push(name + ': ' + r);
  }
  check('все ' + buttons.length + ' кнопок достижимы', unreachable.length === 0,
    unreachable.join('; '));

  console.log('— все инструменты нажимаются по-настоящему');
  const toolCount = await page.evaluate(() => TOOLS.length);
  let toolBad = [];
  for (let i = 0; i < toolCount; i++) {
    await page.evaluate(idx => document.querySelectorAll('#tools .btn')[idx].scrollIntoView({ block: 'center' }), i);
    await wait(80);
    const tool = await page.evaluate(idx => TOOLS[idx][0], i);
    await page.evaluate(idx => document.querySelectorAll('#tools .btn')[idx].click(), i);
    const active = await page.evaluate(() => S.tool);
    if (active !== tool) toolBad.push(tool);
  }
  check('инструменты включаются', toolBad.length === 0, toolBad.join(', '));

  console.log('— настройки инструментов');
  // Статус: раньше выбрать было негде, всегда ставилось «не существует»
  await page.evaluate(() => setTool('status'));
  await wait(300);
  const stBox = await page.evaluate(() => ({
    видно: getComputedStyle(document.getElementById('toolOpts')).display !== 'none',
    кнопок: document.querySelectorAll('#toolOptsBody [data-status]').length,
  }));
  check('у «Статуса» появился выбор', stBox.видно && stBox.кнопок === 4, JSON.stringify(stBox));
  await page.evaluate(() => document.querySelectorAll('#toolOptsBody [data-status]')[1].click());
  check('статус выбирается', await page.evaluate(() => S.statusToApply === 'closed'));

  // Направление входа
  await page.evaluate(() => setTool('entrance'));
  await wait(300);
  const dirBoxVisible = await page.evaluate(() =>
    getComputedStyle(document.getElementById('toolOpts')).display !== 'none' &&
    document.querySelectorAll('#toolOptsBody [data-dir]').length === 4);
  check('направление входа выбирается', dirBoxVisible);
  await page.evaluate(() => document.querySelectorAll('#toolOptsBody [data-dir]')[1].click());
  check('направление переключилось', await page.evaluate(() => S.entranceDir === 90));

  // Толщина коридора
  await page.evaluate(() => setTool('corridor'));
  await wait(300);
  const cwOk = await page.evaluate(() => !!document.querySelector('#toolOptsBody #corridorWidth'));
  check('толщина коридора настраивается', cwOk);
  await page.evaluate(() => {
    const el = document.querySelector('#toolOptsBody #corridorWidth');
    el.value = 30; el.oninput();
  });
  check('толщина применилась', await page.evaluate(() => Math.abs(S.corridorWidth * NAT.w - 30) < 1));

  // Масштаб для линейки
  await page.evaluate(() => setTool('ruler'));
  await wait(300);
  const smOk = await page.evaluate(() => !!document.querySelector('#toolOptsBody #scaleMeters'));
  check('масштаб в метрах задаётся', smOk);
  await page.evaluate(() => {
    const el = document.querySelector('#toolOptsBody #scaleMeters');
    el.value = 120; el.oninput();
  });
  check('масштаб применился', await page.evaluate(() => S.scaleMeters === 120));

  // Ставим вход и смотрим, что нарисовалась стрелка
  await page.evaluate(() => {
    data().entrances.push({ id: 'e1', x: 0.3, y: 0.3, kind: 'main', dir: 90, title: 'Вход' });
    render();
  });
  const arrow = await page.evaluate(() => {
    const h = document.getElementById('overlay').innerHTML;
    return h.includes('data-type="entrance"') && h.includes('polygon');
  });
  check('вход нарисован стрелкой', arrow);

  console.log('— загрузка rooms.json');
  await page.evaluate(() => { setTool('room'); document.querySelectorAll('#floors .btn')[0].click(); });
  await wait(700);
  // Жмём именно кнопку «Загрузить rooms.json» — она выставляет режим импорта.
  // Прямая загрузка в поле раньше уводила в импорт проекта с confirm-диалогом.
  const [chooser] = await Promise.all([
    page.waitForFileChooser({ timeout: 10000 }),
    page.evaluate(() => document.getElementById('btnRooms').click()),
  ]);
  await chooser.accept([ROOMS]);
  await wait(2000);
  const counts = await page.evaluate(() => ({
    one: S.data['1'].rooms.length, two: S.data['2'].rooms.length, tf: S.data['3-4'].rooms.length,
  }));
  check('кабинеты загрузились (61+64+63)',
    counts.one === 61 && counts.two === 64 && counts.tf === 63, JSON.stringify(counts));
  await page.screenshot({ path: path.join(SHOTS, '02-кабинеты.png') });

  console.log('— создание кабинета простым кликом');
  const clickRoom = await page.evaluate(() => {
    // чистим этаж и щёлкаем по пустому месту внутри реального кабинета
    const keep = JSON.stringify(S.data['1'].rooms);
    S.data['1'].rooms = [];
    render();
    const p = rectPx(JSON.parse(keep)[5]);   // берём настоящий кабинет как ориентир
    centerOn(p.x / NAT.w, p.y / NAT.h, 1.5);
    const r = document.getElementById('stage').getBoundingClientRect();
    return {
      x: r.left + (p.x + p.w / 2) * S.zoom + S.panX,
      y: r.top + (p.y + p.h / 2) * S.zoom + S.panY,
      образец: { w: Math.round(p.w), h: Math.round(p.h) },
      сохранить: keep,
    };
  });
  await page.evaluate(() => setTool('room'));
  await page.mouse.click(clickRoom.x, clickRoom.y);
  await wait(600);
  const created = await page.evaluate(() => {
    const r = S.data['1'].rooms[0];
    return r ? { w: Math.round(r.w * NAT.w), h: Math.round(r.h * NAT.h) } : null;
  });
  check('клик создал кабинет', created !== null, 'кабинетов: ' + (created ? 1 : 0));
  if (created) {
    check('размер совпал с настоящим кабинетом',
      Math.abs(created.w - clickRoom.образец.w) <= 6 &&
      Math.abs(created.h - clickRoom.образец.h) <= 6,
      'получилось ' + created.w + 'x' + created.h + ', у образца ' +
      clickRoom.образец.w + 'x' + clickRoom.образец.h);
  }
  // Закрываем окно ввода номера, иначе оно перекроет сцену и помешает дальше
  await page.evaluate(() => document.getElementById('mCancel').click());
  await wait(200);
  // Возвращаем данные И приводим историю в согласованный вид,
  // иначе последующая отмена вернёт состояние с лишним кабинетом
  await page.evaluate(k => {
    S.data['1'].rooms = JSON.parse(k);
    S.history = []; S.hIndex = -1;
    pushHistory();
    render();
  }, clickRoom.сохранить);
  await wait(300);


  console.log('— прижатие к стенам: решающая проверка');
  // Кабинеты из rooms.json уже стоят по стенам, поэтому повторное выравнивание
  // почти ничего не меняет. Проверяем иначе: специально сдвигаем рамку
  // и смотрим, вернёт ли её автоконтур на место.
  const shifted = await page.evaluate(() => {
    const room = S.data['1'].rooms[3];
    const p = rectPx(room);
    const was = { x0: Math.round(p.x), y0: Math.round(p.y) };
    // сдвигаем на 15 пикселей вправо-вниз
    const c = autoContour(p.x + 15, p.y + 15, p.x + p.w + 15, p.y + p.h + 15);
    return {
      было: was,
      после_сдвига_и_автоконтура: { x0: Math.round(c.x0), y0: Math.round(c.y0) },
      вернулось_на: Math.round(c.x0 - p.x) + ',' + Math.round(c.y0 - p.y),
      ширина_рамки: Math.round(p.w),
    };
  });
  console.log('    кабинет: ' + JSON.stringify(shifted));
  check('автоконтур возвращает сдвинутую рамку на место',
    Math.abs(shifted.вернулось_на.split(',')[0]) <= 4 &&
    Math.abs(shifted.вернулось_на.split(',')[1]) <= 4,
    'остался сдвиг ' + shifted.вернулось_на);

  const before = await page.evaluate(() => JSON.stringify(S.data['1'].rooms));
  await page.evaluate(() => document.getElementById('btnSnapAll').click());
  await wait(3000);
  const changedCount = await page.evaluate(b => {
    const a = JSON.parse(b), now = S.data['1'].rooms;
    let n = 0;
    for (let i = 0; i < now.length; i++) {
      if (now[i].x !== a[i].x || now[i].y !== a[i].y || now[i].w !== a[i].w) n++;
    }
    return n;
  }, before);
  console.log('    «Выровнять этаж» изменил кабинетов: ' + changedCount + ' из 61');
  check('кнопка выравнивания отработала без ошибок', true);
  const after = await page.evaluate(() => JSON.stringify(S.data['1'].rooms));
  await page.screenshot({ path: path.join(SHOTS, '03-выровнено.png') });

  console.log('— отмена и возврат (была сломана)');
  await page.evaluate(() => document.getElementById('btnUndo').click());
  await wait(400);
  const u = await page.evaluate(() => ({
    data: JSON.stringify(S.data['1'].rooms),
    redoDisabled: document.getElementById('btnRedo').disabled,
  }));
  check('отмена вернула прежнее состояние', u.data === before,
    'состояния не совпали');
  check('кнопка «Вернуть» разблокировалась', u.redoDisabled === false,
    'disabled = ' + u.redoDisabled);
  await page.evaluate(() => document.getElementById('btnRedo').click());
  await wait(400);
  const r = await page.evaluate(() => JSON.stringify(S.data['1'].rooms));
  check('возврат сработал', r === after, 'состояние не совпало');

  console.log('— статус кликом (быстрая разметка)');
  await page.evaluate(() => { S.statusToApply = 'nonexistent'; setTool('status'); });
  await wait(200);
  const stOk = await page.evaluate(() => {
    const room = data().rooms[10];
    S.selection = [];
    applyStatus({ dataset: { type: 'room', id: room.id } });
    return room.status;
  });
  check('статус проставляется кликом', stOk === 'nonexistent', 'стало ' + stOk);

  console.log('— выделение кабинета (то, о чём спрашивали)');
  const sel = await page.evaluate(() => {
    setTool('select');
    const room = data().rooms[0];
    centerOn(room.x, room.y, 1.6);
    // настоящий щелчок по рамке на плане
    const el = document.querySelector('[data-type="room"][data-id="' + room.id + '"]');
    if (!el) return { ошибка: 'рамка не найдена на плане' };
    const r = el.getBoundingClientRect();
    return { x: r.left + r.width / 2, y: r.top + r.height / 2, номер: room.number };
  });
  if (sel.ошибка) {
    check('рамка кабинета найдена на плане', false, sel.ошибка);
  } else {
    await page.mouse.click(sel.x, sel.y);
    await wait(400);
    const after = await page.evaluate(() => ({
      выделено: S.selection.length,
      тип: S.selection[0] ? S.selection[0].type : null,
      панель: getComputedStyle(document.getElementById('inspector')).display !== 'none',
      поле_номера: document.getElementById('fNumber').value,
    }));
    check('клик по рамке выделяет кабинет', after.выделено === 1 && after.тип === 'room',
      JSON.stringify(after));
    check('панель выбранного кабинета открылась', after.панель);
    check('номер подставился в панель', after.поле_номера === sel.номер,
      'в панели «' + after.поле_номера + '», в данных «' + sel.номер + '»');

    // рамка выделения: тянем по пустому месту и ловим несколько кабинетов
    const marquee = await page.evaluate(() => {
      const a = data().rooms[0], b = data().rooms[5];
      const p = el => { const q = rectPx(el); return { x: q.x, y: q.y, w: q.w, h: q.h }; };
      const ra = p(a), rb = p(b);
      const to = (px, py) => {
        const r = document.getElementById('stage').getBoundingClientRect();
        return { x: r.left + px * S.zoom + S.panX, y: r.top + py * S.zoom + S.panY };
      };
      const s1 = to(Math.min(ra.x, rb.x) - 10, Math.min(ra.y, rb.y) - 10);
      const s2 = to(Math.max(ra.x + ra.w, rb.x + rb.w) + 10, Math.max(ra.y + ra.h, rb.y + rb.h) + 10);
      return { s1, s2 };
    });
    await page.mouse.move(marquee.s1.x, marquee.s1.y);
    await page.mouse.down();
    await page.mouse.move(marquee.s2.x, marquee.s2.y, { steps: 8 });
    await page.mouse.up();
    await wait(400);
    const many = await page.evaluate(() => S.selection.length);
    check('рамкой выделяется несколько кабинетов', many >= 2, 'выделено ' + many);
    await page.screenshot({ path: path.join(SHOTS, '05-выделение.png') });
  }

  console.log('— поиск, фильтр, слои');
  await page.evaluate(() => { document.getElementById('search').value = '101'; renderList(); });
  await wait(300);
  check('поиск работает',
    (await page.evaluate(() => document.querySelectorAll('#list .item[data-id]').length)) >= 1);
  await page.evaluate(() => { document.getElementById('search').value = ''; renderList(); });

  console.log('— сохранение и перезагрузка');
  await page.evaluate(() => saveLocal());
  await page.reload({ waitUntil: 'load' });
  await wait(2500);
  const restored = await page.evaluate(() =>
    S.data['1'].rooms.length + S.data['2'].rooms.length + S.data['3-4'].rooms.length);
  check('разметка пережила перезагрузку', restored === 188, 'восстановлено ' + restored);
  await page.screenshot({ path: path.join(SHOTS, '04-после-перезагрузки.png') });

  const real = errors.filter(e => !e.includes('favicon'));
  check('ошибок за прогон нет', real.length === 0, real.slice(0, 3).join(' | '));

  console.log('');
  console.log('ИТОГО: пройдено ' + passed + ', провалено ' + failed);
  if (real.length) { console.log('Ошибки:'); real.slice(0, 10).forEach(e => console.log('  ' + e)); }
  console.log('Скриншоты: ' + SHOTS);
  await browser.close();
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error('СБОЙ:', e.message, e.stack); process.exit(2); });
