import React from 'react';
import {interpolate} from 'remotion';
import {clamp} from './anim';
import {C, Roboto, SCR} from './theme';

// Оверлей сцены: микро-лейбл UPPERCASE (ls 0.15em) + строка-описание. Вход fade+rise 14f.
export const SceneOverlay: React.FC<{label: string; line: string; frame: number}> = ({label, line, frame}) => {
	const t = interpolate(frame, [0, 14], [0, 1], clamp);
	const rise = interpolate(frame, [0, 14], [14, 0], clamp);
	return (
		<div
			style={{
				position: 'absolute',
				top: 108,
				left: 0,
				width: '100%',
				textAlign: 'center',
				opacity: t,
				transform: `translateY(${rise}px)`,
			}}
		>
			<div style={{fontFamily: Roboto, fontSize: 36, fontWeight: 700, letterSpacing: '0.15em', color: C.blue}}>
				{label}
			</div>
			<div style={{fontFamily: Roboto, fontSize: 44, fontWeight: 500, color: C.title, marginTop: 18, lineHeight: 1.25}}>
				{line}
			</div>
		</div>
	);
};

// Синяя окантовка блока интерфейса. Радиус строго 2px — эстетика приложения.
export const FocusBox: React.FC<{
	fx: number;
	fy: number;
	w: number;
	h: number;
	frame: number;
	at?: number;
}> = ({fx, fy, w, h, frame, at = 0}) => {
	const t = interpolate(frame, [at, at + 12], [0, 1], clamp);
	return (
		<div
			style={{
				position: 'absolute',
				left: `${(fx - w / 2) * 100}%`,
				top: `${(fy - h / 2) * 100}%`,
				width: `${w * 100}%`,
				height: `${h * 100}%`,
				border: `4px solid ${C.blue}`,
				borderRadius: 2,
				opacity: t * 0.9,
				boxShadow: `0 0 0 ${6 * t}px rgba(0,114,206,0.18)`,
			}}
		/>
	);
};

// Пульсирующая точка на временной шкале (реплика живого индикатора): ядро + расходящееся кольцо.
export const PulseDot: React.FC<{fx: number; fy: number; frame: number; period?: number}> = ({
	fx,
	fy,
	frame,
	period = 45,
}) => {
	const t = interpolate(frame, [10, 20], [0, 1], clamp);
	const ph = (frame % period) / period;
	return (
		<div style={{position: 'absolute', left: `${fx * 100}%`, top: `${fy * 100}%`, width: 0, height: 0, opacity: t}}>
			<div
				style={{
					position: 'absolute',
					left: -14,
					top: -14,
					width: 28,
					height: 28,
					borderRadius: 14,
					backgroundColor: C.blue,
					transform: `scale(${interpolate(ph, [0, 1], [1, 2.4])})`,
					opacity: interpolate(ph, [0, 1], [0.5, 0]),
				}}
			/>
			<div
				style={{
					position: 'absolute',
					left: -9,
					top: -9,
					width: 18,
					height: 18,
					borderRadius: 9,
					backgroundColor: C.blue,
					boxShadow: '0 0 0 4px rgba(255,255,255,0.9)',
				}}
			/>
		</div>
	);
};

// Векторный маршрут: пунктир «рисует себя» (strokeDashoffset), маркер кабинета на конце.
export const RouteLine: React.FC<{
	frame: number;
	dur: number;
	points: [number, number][]; // доли экрана
	len?: number;
}> = ({frame, dur, points, len = 1600}) => {
	const path = points.map(([x, y]) => `${(x * SCR.w).toFixed(1)},${(y * SCR.h).toFixed(1)}`).join(' ');
	const [ex, ey] = points[points.length - 1];
	const draw = interpolate(frame, [20, dur - 30], [len, 0], clamp);
	const t = interpolate(frame, [dur - 55, dur - 35], [0, 1], clamp);
	return (
		<svg viewBox={`0 0 ${SCR.w} ${SCR.h}`} style={{position: 'absolute', inset: 0, width: '100%', height: '100%'}}>
			<polyline
				points={path}
				fill="none"
				stroke={C.blue}
				strokeWidth={7}
				strokeLinecap="round"
				strokeLinejoin="round"
				strokeDasharray="18 14"
				strokeDashoffset={draw}
			/>
			<circle cx={ex * SCR.w} cy={ey * SCR.h} r={22} fill={C.blue} opacity={t} />
			<circle cx={ex * SCR.w} cy={ey * SCR.h} r={10} fill="#FFFFFF" opacity={t} />
		</svg>
	);
};

// Плашка номера кабинета над маркером маршрута.
export const RoomChip: React.FC<{fx: number; fy: number; frame: number; at: number}> = ({fx, fy, frame, at}) => {
	const t = interpolate(frame, [at, at + 10], [0, 1], clamp);
	return (
		<div
			style={{
				position: 'absolute',
				left: `${fx * 100}%`,
				top: `${fy * 100}%`,
				translate: '-50% -100%',
				opacity: t,
				scale: `${0.8 + 0.2 * t}`,
			}}
		>
			<div
				style={{
					fontFamily: Roboto,
					fontSize: 30,
					fontWeight: 700,
					color: C.white,
					backgroundColor: C.blue,
					padding: '8px 22px',
					borderRadius: 2,
				}}
			>
				214
			</div>
		</div>
	);
};
