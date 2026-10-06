// Finale3 — S7: чистый финал (фон + aura + эмблема-купол + вордмарк); веер blur-остатков
// экранов убран (vision-QA: месиво внизу кадра читалось как артефакт).
// Эмблема-купол (path'ы из public/icon.svg, fill перекрашен #0B1B3D→#0B3564) 160px +
// aura #E8F0FE a.90 r520 + вордмарк Golos900 108 y640 (blur8→0 scale.94→1 12f на local 10) +
// суб 44 y740 + микро mono32 y800 + общий push-in 1.00→1.03 за 85f (дрейф до конца сцены).
import React from 'react';
import {clamp01, easeInOut} from './anim3';
import {C3, Golos3, Mono3} from './theme3';

// public/icon.svg, fill подменён на #0B3564 (inline-замена при импорте).
const DOME: string[] = [
	'M48,22 C48,20 60,20 60,22 L60,24 L48,24 Z',
	'M44,25 L64,25 L64,28 L44,28 Z',
	'M39,29 L69,29 L69,32 L39,32 Z',
	'M34,33 L74,33 L74,36 L34,36 Z',
	'M24,38 L84,38 L84,42 L24,42 Z',
	'M26,43 L82,43 L82,45 L26,45 Z',
	'M25,46 L31,46 L31,48 L25,48 Z M26,48 L30,48 L30,73 L26,73 Z M25,73 L31,73 L31,75 L25,75 Z',
	'M35,46 L41,46 L41,48 L35,48 Z M36,48 L40,48 L40,73 L36,73 Z M35,73 L41,73 L41,75 L35,75 Z',
	'M45,46 L51,46 L51,48 L45,48 Z M46,48 L50,48 L50,73 L46,73 Z M45,73 L51,73 L51,75 L45,75 Z',
	'M57,46 L63,46 L63,48 L57,48 Z M58,48 L62,48 L62,73 L58,73 Z M57,73 L63,73 L63,75 L57,75 Z',
	'M67,46 L73,46 L73,48 L67,48 Z M68,48 L72,48 L72,73 L68,73 Z M67,73 L73,73 L73,75 L67,75 Z',
	'M77,46 L83,46 L83,48 L77,48 Z M78,48 L82,48 L82,73 L78,73 Z M77,73 L83,73 L83,75 L77,75 Z',
	'M23,76 L85,76 L85,79 L23,79 Z',
	'M20,80 L88,80 L88,83 L20,83 Z',
	'M17,84 L91,84 L91,87 L17,87 Z',
];

export const Emblem3: React.FC<{size: number; color?: string}> = ({size, color = C3.navy}) => (
	<svg width={size} height={size} viewBox="0 0 108 108">
		{DOME.map((d, i) => (
			<path key={i} fill={color} d={d} />
		))}
	</svg>
);

export const Finale3: React.FC<{frame: number; pushInStart?: number}> = ({frame, pushInStart = 30}) => {
	const push = 1 + 0.03 * easeInOut((frame - pushInStart) / 85);
	const wm = clamp01((frame - 10) / 12);
	return (
		<div style={{position: 'absolute', inset: 0, transform: `scale(${push})`}}>
			{/* aura #E8F0FE a.90 r520 вокруг эмблемы (540,400) */}
			<div
				style={{
					position: 'absolute',
					left: 540 - 520,
					top: 400 - 520,
					width: 1040,
					height: 1040,
					background: `radial-gradient(circle at 50% 50%, ${C3.radial} 0%, rgba(232,240,254,0) 70%)`,
					opacity: 0.9,
				}}
			/>
			<div style={{position: 'absolute', left: 540 - 80, top: 400 - 80}}>
				<Emblem3 size={160} />
			</div>
			<div
				style={{
					position: 'absolute',
					top: 586, // y640 по центру строки
					width: '100%',
					textAlign: 'center',
					opacity: wm,
					filter: `blur(${8 * (1 - wm)}px)`,
					transform: `scale(${0.94 + 0.06 * wm})`,
				}}
			>
				<span style={{fontFamily: Golos3, fontWeight: 900, fontSize: 108, letterSpacing: '-1%', color: C3.navy}}>
					Мой Политех
				</span>
			</div>
			<div
				style={{
					position: 'absolute',
					top: 718, // y740 по центру
					width: '100%',
					textAlign: 'center',
					opacity: clamp01((frame - 22) / 8),
				}}
			>
				<span style={{fontFamily: Golos3, fontWeight: 500, fontSize: 44, color: C3.sub}}>Телефон наконец по делу</span>
			</div>
			<div
				style={{
					position: 'absolute',
					top: 784, // y800 по центру
					width: '100%',
					textAlign: 'center',
					opacity: clamp01((frame - 30) / 8),
				}}
			>
				<span style={{fontFamily: Mono3, fontWeight: 600, fontSize: 32, color: C3.sub}}>скоро в твоём телефоне</span>
			</div>
		</div>
	);
};
