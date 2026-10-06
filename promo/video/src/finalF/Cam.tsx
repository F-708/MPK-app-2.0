// Cam — детерминированная камера: ключи {f,s,x,y}, x/y — фокус в px шота.
// transform: translate3d+scale вокруг центра экрана; дети (ShotLayer) едут вместе.
import React from 'react';
import {Easing, Img, staticFile} from 'remotion';
import {CROP, CX, CY, K, LAYER_H, SCR_W, SHOT_H, SHOT_W} from './theme';

export type CamKey = {f: number; s: number; x: number; y: number};

const at = (keys: CamKey[], frame: number, get: (k: CamKey) => number) => {
	const last = keys[keys.length - 1];
	if (frame <= keys[0].f) return get(keys[0]);
	if (frame >= last.f) return get(last);
	for (let i = 0; i < keys.length - 1; i++) {
		const a = keys[i];
		const b = keys[i + 1];
		if (frame >= a.f && frame <= b.f) {
			const t = (frame - a.f) / (b.f - a.f);
			return get(a) + (get(b) - get(a)) * Easing.inOut(Easing.cubic)(t);
		}
	}
	return get(last);
};

export const Cam: React.FC<{keys: CamKey[]; frame: number; children: React.ReactNode}> = ({
	keys,
	frame,
	children,
}) => {
	const s = at(keys, frame, (k) => k.s);
	const px = at(keys, frame, (k) => k.x) * K;
	const py = at(keys, frame, (k) => k.y) * K - CROP;
	return (
		<div style={{position: 'absolute', inset: 0, overflow: 'hidden'}}>
			<div
				style={{
					position: 'absolute',
					left: 0,
					top: -CROP,
					width: SCR_W,
					height: LAYER_H,
					transform: `translate3d(${s * (CX - px)}px, ${s * (CY - py)}px, 0) scale(${s})`,
					transformOrigin: `${CX}px ${CY}px`,
				}}
			>
				{children}
			</div>
		</div>
	);
};

// Слой шота: Img в полный размер слоя; оверлеи — дети в координатах шота.
export const ShotLayer: React.FC<{shot: string; children?: React.ReactNode}> = ({shot, children}) => (
	<div style={{position: 'absolute', inset: 0}}>
		<Img src={staticFile(`shots/${shot}.png`)} style={{width: '100%', height: '100%', display: 'block'}} />
		{children}
	</div>
);

// SVG-оверлей в координатах шота (1648×3660).
export const ShotSvg: React.FC<{children: React.ReactNode}> = ({children}) => (
	<svg
		viewBox={`0 0 ${SHOT_W} ${SHOT_H}`}
		style={{position: 'absolute', inset: 0, width: '100%', height: '100%', overflow: 'visible'}}
	>
		{children}
	</svg>
);

// Div-оверлей: x,y — точка в px шота (левый верх), дальше обычные screen-px.
export const ShotDiv: React.FC<{
	x: number;
	y: number;
	children: React.ReactNode;
	style?: React.CSSProperties;
}> = ({x, y, children, style}) => (
	<div style={{position: 'absolute', left: x * K, top: y * K, ...style}}>{children}</div>
);
