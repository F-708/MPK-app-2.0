// QA-стилы PromoFinal9x16-d4: 9 контрольных кадров → out/qa_d4/. Запуск: node scripts/qa-d4.mjs
import {spawnSync} from 'node:child_process';
import {mkdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

const ROOT = fileURLToPath(new URL('..', import.meta.url));
mkdirSync(`${ROOT}/out/qa_d4`, {recursive: true});
for (const f of [60, 180, 300, 345, 470, 640, 700, 820, 895]) {
	const out = `out/qa_d4/f${String(f).padStart(3, '0')}.png`;
	const r = spawnSync('npx', ['remotion', 'still', 'PromoFinal9x16-d4', out, `--frame=${f}`], {
		stdio: 'inherit',
		cwd: ROOT,
		shell: process.platform === 'win32',
	});
	if (r.status !== 0) {
		console.error(`[qa-d4] FAIL на кадре ${f}`);
		process.exit(1);
	}
}
console.log('[qa-d4] PASS: 9 стилов в out/qa_d4/');
