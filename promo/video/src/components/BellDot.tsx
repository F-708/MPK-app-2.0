import React from 'react';
import {interpolate, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, SP} from '../theme';

// Бегущая точка по рельсу звонков — как в приложении (BellsScreen DotPulse):
// позиция по времени линейным tween'ом, ореол-пульс 4.5→7dp, tween 900мс Reverse.
export const BellDot: React.FC<{from: [number, number]; to: [number, number]}> = ({from, to}) => {
	const frame = useCurrentFrame();
	const {fps, durationInFrames: dur} = useVideoConfig();
	const t = interpolate(frame, [0, dur], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	const x = interpolate(t, [0, 1], [from[0], to[0]]);
	const y = interpolate(t, [0, 1], [from[1], to[1]]);
	// 900мс Reverse → треугольная волна с периодом 1800мс
	const phase = ((frame / fps) % 1.8) / 1.8;
	const tri = phase < 0.5 ? phase * 2 : 2 - phase * 2;
	const halo = (4.5 + 2.5 * tri) * SP;

	return (
		<div style={{position: 'absolute', left: x, top: y, translate: '-50% -50%', width: 0, height: 0}}>
			<div
				style={{
					position: 'absolute',
					left: -halo,
					top: -halo,
					width: halo * 2,
					height: halo * 2,
					borderRadius: '50%',
					backgroundColor: 'rgba(0,114,206,0.35)',
				}}
			/>
			<div
				style={{
					position: 'absolute',
					left: -10,
					top: -10,
					width: 20,
					height: 20,
					borderRadius: '50%',
					backgroundColor: C.blue,
					border: '3px solid #FFFFFF',
					boxSizing: 'border-box',
				}}
			/>
		</div>
	);
};
