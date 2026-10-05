import React from 'react';
import {interpolate, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, Mono} from '../theme';

// SecondsCard (S4): моно JetBrains 700 96px, «4:32»→«4:29», янтарная рамка 2px,
// pop-in spring на бите (abs 180 = кадр 18 сцены). Тик детерминированно по frame.
// ponytail: tickEvery=12f (0.4с) — иначе «pop на 180» и финал «4:29» не сходятся
// на битовой сетке (18f×3 тика после pop'а дают только 4:30); если закажут строгий
// 1с тик — сдвинуть popAt на 0 сцены и tickEvery=18.
export const SecondsCard: React.FC<{
	left: number;
	top: number;
	popAt: number;
	startSec?: number;
	tickEvery?: number;
}> = ({left, top, popAt, startSec = 272, tickEvery = 12}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const pop = spring({frame: frame - popAt, fps, config: {damping: 12}});
	const sec = startSec - Math.max(0, Math.floor((frame - popAt) / tickEvery));
	return (
		<div
			style={{
				position: 'absolute',
				left,
				top,
				opacity: interpolate(pop, [0, 1], [0, 1]),
				transform: `scale(${interpolate(pop, [0, 1], [0.7, 1])})`,
				backgroundColor: 'rgba(5,7,12,0.88)',
				border: `2px solid ${C.amber}`,
				borderRadius: 28,
				padding: '24px 36px',
				boxShadow: '0 24px 60px rgba(0,0,0,0.45)',
			}}
		>
			<div
				style={{
					fontFamily: Mono,
					fontWeight: 700,
					fontSize: 96,
					lineHeight: 1.1,
					color: '#FFFFFF',
					fontVariantNumeric: 'tabular-nums',
				}}
			>
				{Math.floor(sec / 60)}:{String(sec % 60).padStart(2, '0')}
			</div>
		</div>
	);
};
