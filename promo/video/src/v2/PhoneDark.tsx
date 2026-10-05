import React from 'react';
import {Img, interpolate, staticFile, useCurrentFrame} from 'remotion';

// Геометрия v2 (VIDEO_V2_SPEC.md): bezel #05070C r56 border12, экран 832×1664 r40,
// тень 0 60px 120px rgba(0,0,0,.55). Все шоты 824×1830.
export const PHONE = {w: 856, h: 1688};
export const SCREEN = {w: 832, h: 1664};
export const SHOT = {w: 824, h: 1830};
// contain-маппинг: бокс img в экране → width = 1664*(824/1830) = 749.2, ox = (832-749.2)/2 ≈ 41.4.
export const IMG_W = (SCREEN.h * SHOT.w) / SHOT.h; // 749.2
export const OX = (SCREEN.w - IMG_W) / 2; // 41.4
// Пиксель шота (ix,iy) → px в экране (contain). Оверлеи сажать ВНУТРИ transformed-контейнера шота.
export const shotPt = (ix: number, iy: number) => ({
	left: OX + (ix / SHOT.w) * IMG_W,
	top: (iy / SHOT.h) * SCREEN.h,
});

// Кадр приложения в экран (contain по умолчанию, якорь верх; cover — для зум-сцен).
export const ScreenImg: React.FC<{
	name: string;
	fit?: 'contain' | 'cover';
	position?: string;
	style?: React.CSSProperties;
}> = ({name, fit = 'contain', position = '50% 0%', style}) => (
	<Img
		src={staticFile(`shots/${name}.png`)}
		style={{position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: fit, objectPosition: position, ...style}}
	/>
);

// Тёмный телефон. scale — масштаб всей коробки (оверлеи живут в экранных px и масштабируются вместе),
// sweepFrom — кадр glass-sweep (диагональная белая полоса opacity .06, 12 кадров; один раз за фильм, S2).
export const PhoneDark: React.FC<{
	scale?: number;
	left: number;
	top: number;
	sweepFrom?: number;
	style?: React.CSSProperties;
	children: React.ReactNode;
}> = ({scale = 1, left, top, sweepFrom, style, children}) => {
	const frame = useCurrentFrame();
	const sweep = sweepFrom === undefined ? 0 : interpolate(frame, [sweepFrom, sweepFrom + 12], [-500, 1400], {
		extrapolateLeft: 'clamp',
		extrapolateRight: 'clamp',
	});
	const sweepOn = sweepFrom !== undefined && frame >= sweepFrom && frame < sweepFrom + 12;
	return (
		<div style={{position: 'absolute', left, top, width: PHONE.w * scale, height: PHONE.h * scale, ...style}}>
			<div
				style={{
					width: PHONE.w,
					height: PHONE.h,
					transform: `scale(${scale})`,
					transformOrigin: 'top left',
					backgroundColor: '#05070C',
					border: '12px solid #05070C',
					borderRadius: 56,
					boxSizing: 'border-box',
					boxShadow: '0 60px 120px rgba(0,0,0,0.55)',
				}}
			>
				<div style={{position: 'relative', width: SCREEN.w, height: SCREEN.h, borderRadius: 40, overflow: 'hidden', backgroundColor: '#0B1B3D'}}>
					{children}
					{sweepOn && (
						<div
							style={{
								position: 'absolute',
								left: sweep,
								top: -400,
								width: 260,
								height: 2600,
								background: '#FFFFFF',
								opacity: 0.06,
								transform: 'rotate(20deg)',
								pointerEvents: 'none',
							}}
						/>
					)}
				</div>
			</div>
		</div>
	);
};
