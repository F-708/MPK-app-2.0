import {interpolate} from 'remotion';

export const clamp = {extrapolateLeft: 'clamp' as const, extrapolateRight: 'clamp' as const};

// Ключи камеры/анимации: равномерные точки по длительности сцены.
export const cam = (arr: number[], frame: number, dur: number) =>
	interpolate(frame, arr.map((_, i) => Math.round((i * dur) / Math.max(1, arr.length - 1))), arr, clamp);

// Фейд сцены: вход inf кадров, выход outf кадров.
export const fade = (frame: number, dur: number, inf = 8, outf = 8) =>
	interpolate(frame, [0, inf, dur - outf, dur], [0, 1, 1, 0], clamp);
