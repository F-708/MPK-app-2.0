// Readability-гейт v3 (VIDEO_V3_FINAL.md): титул живёт ≥ (len/15 + 1.0)s до конца сцены.
// Таблица — src/v3/copy.json (единый источник копи и длительностей, master + cut-down).
// Запуск: npm run qa3
import {readFileSync} from 'node:fs';

const copy = JSON.parse(readFileSync(new URL('../src/v3/copy.json', import.meta.url)));
const TITLE_IN = copy.titleIn;
const req = (dur) => (dur / 30 / 15 + 1.0) * 30;

const rows = [];
for (const s of copy.scenes) if (s.title) rows.push({scene: s.id, title: s.title.join(' '), dur: s.dur});
for (const c of copy.cut || []) {
	const s = copy.scenes.find((x) => x.id === c.id);
	if (s?.title) rows.push({scene: `${c.id}(15s)`, title: s.title.join(' '), dur: c.dur});
}

let fail = 0;
for (const r of rows) {
	const on = r.dur - TITLE_IN;
	const need = req(r.dur);
	const ok = on >= need;
	if (!ok) fail++;
	console.log(
		`${ok ? 'PASS' : 'FAIL'} ${r.scene.padEnd(8)} dur=${String(r.dur).padStart(3)}f titleOn=${String(on).padStart(3)}f need≥${need.toFixed(1)}f  «${r.title}»`,
	);
}
if (fail) {
	console.error(`[readability] FAIL: ${fail} сцен(ы)`);
	process.exit(1);
}
console.log(`[readability] PASS: ${rows.length} сцен(ы)`);
