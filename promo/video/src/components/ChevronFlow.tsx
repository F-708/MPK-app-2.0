import React from 'react';
import {useCurrentFrame, useVideoConfig} from 'remotion';
import {C} from '../theme';

type Pt = [number, number];

// Оверлей бегущих шевронов поверх статичного кадра карты — реплицирует параметры
// приложения (CollegeMapScreen): ROUTE_TICK_STEP_PX=56, SPEED=12 px/с, плечи ±35°,
// вершина впереди, arm=0.75·size (BB7).
const STEP = 56;
const SPEED = 12;
const SIZE = 26; // ROUTE_TICK_SIZE_DP=10 (половина габарита) → экр. px кадра
const ARM = 0.75 * SIZE;
const COS35 = 0.8192;
const SIN35 = 0.5736;

export const ChevronFlow: React.FC<{points: Pt[]}> = ({points}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();

	// накопленные длины сегментов полилинии
	const segs: {a: Pt; b: Pt; len: number; acc: number; dx: number; dy: number}[] = [];
	let total = 0;
	for (let i = 0; i < points.length - 1; i++) {
		const [ax, ay] = points[i];
		const [bx, by] = points[i + 1];
		const len = Math.hypot(bx - ax, by - ay);
		segs.push({a: points[i], b: points[i + 1], len, acc: total, dx: (bx - ax) / len, dy: (by - ay) / len});
		total += len;
	}
	const at = (d: number) => {
		const s = segs.find((sg) => d < sg.acc + sg.len) ?? segs[segs.length - 1];
		const t = d - s.acc;
		return {p: [s.a[0] + s.dx * t, s.a[1] + s.dy * t] as Pt, dx: s.dx, dy: s.dy};
	};

	const offset = ((frame / fps) * SPEED) % STEP;
	const chevrons: React.ReactNode[] = [];
	for (let d = offset, i = 0; d < total - SIZE; d += STEP, i++) {
		const {p, dx, dy} = at(d);
		const nx = -dy;
		const ny = dx;
		const apex: Pt = [p[0] + 0.3 * SIZE * dx, p[1] + 0.3 * SIZE * dy];
		const backX = 0.3 * SIZE - ARM * COS35;
		const spread = ARM * SIN35;
		const e1: Pt = [p[0] + backX * dx + spread * nx, p[1] + backX * dy + spread * ny];
		const e2: Pt = [p[0] + backX * dx - spread * nx, p[1] + backX * dy - spread * ny];
		chevrons.push(
			<path
				key={i}
				d={`M${e1[0]},${e1[1]} L${apex[0]},${apex[1]} L${e2[0]},${e2[1]}`}
				fill="none"
				stroke="#FFFFFF"
				strokeWidth={5}
				strokeLinecap="round"
				strokeLinejoin="round"
			/>,
		);
	}

	return (
		<svg width="100%" height="100%" style={{position: 'absolute', inset: 0}}>
			<polyline
				points={points.map((p) => p.join(',')).join(' ')}
				fill="none"
				stroke={C.blue}
				strokeWidth={7}
				strokeLinecap="round"
				strokeLinejoin="round"
			/>
			{chevrons}
		</svg>
	);
};
