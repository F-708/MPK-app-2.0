// ChevronOverlay3 — SVG-шевроны ТОЧНО по родной линии маршрута шота S1 (реплика анимации
// приложения). Ломаная измерена по shot_map_route (824×1830): родной маршрут «главный вход →
// лестница у 132/214, шаг 1» — вертикальная осевая линия коридора cor_xavktf
// (эт1 [0.4809,0.2537→0.4836,0.5173] по route-check AA8): в шоте x≈411px, y 618→1478px,
// т.е. доли шота (0.4988, 0.3377)→(0.4988, 0.8077); в crop-координатах S1 x[0.02,0.98] y[0.16,0.62]
// → x≈0.4988, y 0.3863→1.408 (низ уходит за crop — шевроны вытекают кромкой карточки).
// Скорость 12px/s, шаг 48px, размер 10px, #0072CE с белым ядром. Бегут с кадра 0.
import React from 'react';
import {C3} from './theme3';

// точки в долях карточки (crop S1)
const LINE: [number, number][] = [
	[0.4988, 0.3863],
	[0.4988, 1.04],
];
const STEP = 48;
const SPEED_PX_PER_F = 12 / 30;

export const ChevronOverlay3: React.FC<{frame: number; w: number; h: number}> = ({frame, w, h}) => {
	const pts = LINE.map(([x, y]) => [x * w, y * h] as [number, number]);
	const segs: {x: number; y: number; ang: number; d: number}[] = [];
	let total = 0;
	for (let i = 0; i < pts.length - 1; i++) {
		const [ax, ay] = pts[i];
		const [bx, by] = pts[i + 1];
		const len = Math.hypot(bx - ax, by - ay);
		segs.push({x: ax, y: ay, ang: (Math.atan2(by - ay, bx - ax) * 180) / Math.PI, d: total});
		total += len;
	}
	const offset = (frame * SPEED_PX_PER_F) % STEP;
	const chevrons: {x: number; y: number; ang: number}[] = [];
	for (let d = offset; d < total; d += STEP) {
		const s = segs.find((sg, i) => d >= sg.d && (i === segs.length - 1 || d < segs[i + 1].d));
		if (!s) continue;
		const t = d - s.d;
		const rad = (s.ang * Math.PI) / 180;
		chevrons.push({x: s.x + t * Math.cos(rad), y: s.y + t * Math.sin(rad), ang: s.ang});
	}
	const path = 'M -7 -5 L 0 0 L 7 -5'; // «⌄» 14×5 → высота 10px по спеке
	return (
		<svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} style={{position: 'absolute', inset: 0}}>
			{chevrons.map((c, i) => (
				<g key={i} transform={`translate(${c.x} ${c.y}) rotate(${c.ang - 90})`}>
					<path d={path} fill="none" stroke={C3.blue} strokeWidth={3.5} strokeLinecap="round" />
					<path d={path} fill="none" stroke={C3.white} strokeWidth={1.4} strokeLinecap="round" />
				</g>
			))}
		</svg>
	);
};
