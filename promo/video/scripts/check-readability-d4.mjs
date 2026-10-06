// Readability-гейт finalF (PromoFinal9x16-d4): каждая строка плашки живёт ≥ (len/15 + 1.0)s.
// Таблица — src/finalF/copy.json. Запуск: node scripts/check-readability-d4.mjs
import {readFileSync} from 'node:fs';

const fin = JSON.parse(readFileSync(new URL('../src/finalF/copy.json', import.meta.url)));
let fail = 0;
let count = 0;
for (const s of fin.scenes)
	if (s.title)
		for (const line of s.title) {
			count++;
			const on = s.dur - fin.titleIn;
			const need = (line.length / 15 + 1.0) * 30;
			const ok = on >= need;
			if (!ok) fail++;
			console.log(
				`${ok ? 'PASS' : 'FAIL'} ${s.id.padEnd(8)} line=${String(line.length).padStart(2)} on=${String(on).padStart(3)}f need≥${need.toFixed(1)}f  «${line}»`,
			);
		}
if (fail) {
	console.error(`[readability-d4] FAIL: ${fail} строк(и)`);
	process.exit(1);
}
console.log(`[readability-d4] PASS: ${count} строк(и)`);
