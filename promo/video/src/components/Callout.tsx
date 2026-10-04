import React from 'react';
import {interpolate, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, Golos} from '../theme';

// Янтарная выноска СНАРУЖИ мокапа (п.1.1): линия + стрелка + подпись 34px Golos 500,
// pop-in spring 250мс. Режим rect — янтарная рамка/штрих вокруг области (дорисовка штриха).
export const Callout: React.FC<{
	x: number; // кончик стрелки (или x рамки), координаты кадра 9:16
	y: number;
	lx?: number; // центр подписи (в rect-режиме не нужен)
	ly?: number;
	label?: string;
	delay?: number;
	rect?: {w: number; h: number}; // если задан — рамка (x,y = левый верхний угол)
}> = ({x, y, lx = x, ly = y, label, delay = 0, rect}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const f = Math.max(0, frame - delay);
	const pop = spring({frame: f, fps, config: {damping: 150, stiffness: 200}, durationInFrames: 8});
	if (f <= 0) return null;

	const draw = interpolate(f, [0, 12], [1, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});

	return (
		<div
			style={{
				position: 'absolute',
				inset: 0,
				opacity: pop,
				transformOrigin: `${lx}px ${ly}px`,
				transform: `scale(${interpolate(pop, [0, 1], [0.7, 1])})`,
			}}
		>
			<svg width={1080} height={1920} style={{position: 'absolute', inset: 0}}>
				{rect ? (
					<rect
						x={x}
						y={y}
						width={rect.w}
						height={rect.h}
						rx={24}
						fill="none"
						stroke={C.amber}
						strokeWidth={8}
						strokeDasharray={2 * (rect.w + rect.h)}
						strokeDashoffset={draw * 2 * (rect.w + rect.h)}
					/>
				) : (
					<>
						<line x1={lx} y1={ly} x2={x} y2={y} stroke={C.amber} strokeWidth={7} strokeLinecap="round" />
						<ArrowHead x={x} y={y} fx={lx} fy={ly} />
					</>
				)}
			</svg>
			{label && (
				<div
					style={{
						position: 'absolute',
						left: lx,
						top: ly,
						translate: '-50% -50%',
						backgroundColor: 'rgba(255,255,255,0.92)',
						borderRadius: 14,
						padding: '8px 20px',
						fontFamily: Golos,
						fontWeight: 500,
						fontSize: 34,
						color: C.ink,
						whiteSpace: 'nowrap',
						boxShadow: '0 4px 16px rgba(11,53,100,0.15)',
					}}
				>
					{label}
				</div>
			)}
		</div>
	);
};

const ArrowHead: React.FC<{x: number; y: number; fx: number; fy: number}> = ({x, y, fx, fy}) => {
	const a = Math.atan2(y - fy, x - fx);
	const L = 30;
	const w = 0.42;
	const p1 = [x - L * Math.cos(a - w), y - L * Math.sin(a - w)];
	const p2 = [x - L * Math.cos(a + w), y - L * Math.sin(a + w)];
	return <path d={`M${p1[0]},${p1[1]} L${x},${y} L${p2[0]},${p2[1]} Z`} fill={C.amber} />;
};
