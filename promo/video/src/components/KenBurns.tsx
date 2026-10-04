import React from 'react';
import {interpolate, useCurrentFrame, useVideoConfig} from 'remotion';

// Ken Burns камеры поверх статичного кадра (п.4.5: linear, scale ≤1.06–1.35).
// Ключи — равномерные точки по длительности последовательности.
export const KenBurns: React.FC<{
	scale?: number[];
	x?: number[];
	y?: number[];
	children: React.ReactNode;
}> = ({scale = [1, 1.06], x = [0, 0], y = [0, 0], children}) => {
	const frame = useCurrentFrame();
	const {durationInFrames: dur} = useVideoConfig();
	const range = (arr: number[]) => arr.map((_, i) => (i * dur) / Math.max(1, arr.length - 1));
	const at = (arr: number[]) =>
		interpolate(frame, range(arr), arr, {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	return (
		<div style={{position: 'absolute', inset: 0, overflow: 'hidden'}}>
			<div
				style={{
					position: 'absolute',
					inset: 0,
					scale: `${at(scale)}`,
					translate: `${at(x)}px ${at(y)}px`,
				}}
			>
				{children}
			</div>
		</div>
	);
};
