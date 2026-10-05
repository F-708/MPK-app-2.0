// Синк кадров из promo/shots/ в public/shots/ + манифест наличия public/shots.json.
// Запускается перед studio/render/still (npm run sync). Кадры производит параллельный
// работник — отсутствующие имена сцены рисуют заглушкой (Shot.tsx).
import {cpSync, existsSync, mkdirSync, readdirSync, writeFileSync} from 'node:fs';

const SRC = new URL('../../shots/', import.meta.url);
const DST = new URL('../public/shots/', import.meta.url);

// Имена кадров работника → слоты сцен (кадр = один файл, замена точечная).
const ALIAS = {
	shot_schedule_tomorrow: 'a3_lessons',
	shot_map_route: 'a2_map_route',
	shot_bells: 'a4_bells_timeline',
	shot_tasks: 'a7_task_dialog',
	shot_materials: 'a8_materials_list',
	shot_viewer_text: 'a8_material_pdf',
	shot_grades: 'a6_grades',
};

mkdirSync(DST, {recursive: true});
const have = {};
if (existsSync(SRC)) {
	for (const f of readdirSync(SRC)) {
		const m = f.match(/^(.+)\.(png|mp4|jpg|webm)$/i);
		if (!m) continue;
		cpSync(new URL(f, SRC), new URL(f, DST));
		have[m[1]] = m[2].toLowerCase();
	}
}
for (const [src, dst] of Object.entries(ALIAS)) {
	if (have[src] && !have[dst]) {
		cpSync(new URL(`${src}.${have[src]}`, DST), new URL(`${dst}.${have[src]}`, DST));
		have[dst] = have[src];
	}
}

writeFileSync(new URL('../public/shots.json', import.meta.url), JSON.stringify(have, null, 1));
console.log(`[sync-shots] ${Object.keys(have).length} файл(ов):`, Object.keys(have).join(', ') || '—');
