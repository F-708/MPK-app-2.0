import React from 'react';
import {interpolate, useCurrentFrame, useVideoConfig} from 'remotion';
import {MOCK_9, MOCK_16, NOTCH} from '../theme';

// Интерполяция ключей камеры: равномерные точки по длительности последовательности.
export const kbAt = (arr: number[], frame: number, dur: number) =>
	interpolate(frame, arr.map((_, i) => (i * dur) / Math.max(1, arr.length - 1)), arr, {
		extrapolateLeft: 'clamp',
		extrapolateRight: 'clamp',
	});

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
	const at = (arr: number[]) => kbAt(arr, frame, dur);
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

// Пиксель шота (ix, iy) → координаты оверлея сцены (пространство 9:16, в котором живёт
// Callout; в wide Stage домасштабирует оверлей к мокапу сам). Повторяет посадку Shot:
// contain/cover в экран мокапа (letterbox по objectPosition 50%), затем трансформ камеры
// KenBurns (scale/translate вокруг центра экрана). В 16:9 экран опущен на NOTCH (Layout).
// Без этого кончики выносок живут отдельно от едущего зума (vision-QA3 f420/f460).
export const projectShot =
	(
		o: {wide: boolean; iw: number; ih: number; fit: 'contain' | 'cover'; scale: number[]; x?: number[]; y?: number[]},
		frame: number,
		dur: number,
	) =>
	(ix: number, iy: number) => {
		const {wide, iw, ih, fit} = o;
		const mock = wide ? MOCK_16 : MOCK_9;
		const notch = wide ? NOTCH : 0;
		const sw = mock.w - 28;
		const sh = mock.h - 28 - notch;
		const tlx = mock.x + 14;
		const tly = mock.y + 14 + notch;
		const m = fit === 'contain' ? Math.min(sw / iw, sh / ih) : Math.max(sw / iw, sh / ih);
		const sx = (sw - iw * m) / 2 + m * ix;
		const sy = (sh - ih * m) / 2 + m * iy;
		const s = kbAt(o.scale, frame, dur);
		const tx = kbAt(o.x ?? [0, 0], frame, dur);
		const ty = kbAt(o.y ?? [0, 0], frame, dur);
		const X = tlx + sw / 2 + s * (sx - sw / 2) + tx;
		const Y = tly + sh / 2 + s * (sy - sh / 2) + ty;
		if (!wide) return {x: X, y: Y};
		const k = MOCK_16.w / MOCK_9.w;
		return {x: (X - (MOCK_16.x - k * MOCK_9.x)) / k, y: (Y - (MOCK_16.y - k * MOCK_9.y)) / k};
	};
