import React from 'react';
import {AbsoluteFill, Easing, interpolate, useCurrentFrame} from 'remotion';

// Stage v2 — тёмная брендовая сцена (VIDEO_V2_SPEC.md): #0B3564 + radial #0E4178→#082647,
// SVG-сетка маршрутных линий opacity .04 с дрейфом 1px/с (frame/30 — детерминированно).
export const SAFE = {x: 96, y: 200};

const ROUTES = [
	'M -120 300 L 300 300 L 420 420 L 420 900 L 700 1180 L 1220 1180',
	'M -120 1500 L 250 1500 L 500 1250 L 900 1250 L 1100 1450 L 1320 1450',
	'M 200 -120 L 200 250 L 380 430 L 380 1000 L 560 1180 L 560 2040',
	'M 800 -120 L 800 200 L 950 350 L 950 800 L 1250 1100',
	'M -120 800 L 150 800 L 350 600 L 750 600 L 950 400 L 1320 400',
	'M 100 2040 L 100 1700 L 300 1500 L 780 1500 L 980 1700 L 980 2040',
];

export const Stage15: React.FC<{children: React.ReactNode}> = ({children}) => {
	const frame = useCurrentFrame();
	const drift = frame / 30; // 1px/с
	return (
		<AbsoluteFill
			style={{
				backgroundColor: '#0B3564',
				backgroundImage: 'radial-gradient(115% 85% at 50% 42%, #0E4178 0%, #0B3564 55%, #082647 100%)',
			}}
		>
			<svg
				width={1080}
				height={1920}
				viewBox="0 0 1080 1920"
				style={{position: 'absolute', inset: 0, opacity: 0.04, translate: `${-drift}px 0`}}
			>
				{ROUTES.map((d) => (
					<path key={d} d={d} fill="none" stroke="#FFFFFF" strokeWidth={3} strokeLinejoin="round" strokeLinecap="round" />
				))}
			</svg>
			{children}
		</AbsoluteFill>
	);
};

// 4-кадровый push на бите (spec: склейки hard-cut/push 4 кадра, без fade).
export const Push: React.FC<{children: React.ReactNode}> = ({children}) => {
	const frame = useCurrentFrame();
	const y = interpolate(frame, [0, 4], [56, 0], {
		extrapolateLeft: 'clamp',
		extrapolateRight: 'clamp',
		easing: Easing.out(Easing.cubic),
	});
	return <AbsoluteFill style={{translate: `0 ${y}px`}}>{children}</AbsoluteFill>;
};
