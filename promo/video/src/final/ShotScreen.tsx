import React from 'react';
import {Img, staticFile, useCurrentFrame} from 'remotion';
import {cam} from './anim';
import {SCR} from './theme';

// Экран телефона со шотом и камерой Pan & Zoom: transform translate3d+scale вокруг
// центра экрана (origin 50% 50%). Оверлеи-children позиционируются в долях экрана
// (fx, fy ∈ 0..1) и едут вместе с камерой. Фокус: t = s·(0.5−f) — точка (fx, fy)
// приводится к центру при зуме s; при s=1 перевода нет.
export const ShotScreen: React.FC<{
	shot: string; // имя файла в public/shots/
	dur: number;
	scale?: number[]; // ключи зума по сцене
	fx?: number[]; // ключи фокуса x [0..1]
	fy?: number[]; // ключи фокуса y [0..1]
	children?: React.ReactNode;
}> = ({shot, dur, scale = [1, 1], fx = [0.5, 0.5], fy = [0.5, 0.5], children}) => {
	const frame = useCurrentFrame();
	const s = cam(scale, frame, dur);
	const fxc = cam(fx, frame, dur);
	const fyc = cam(fy, frame, dur);
	const x = s * (0.5 - fxc) * SCR.w;
	const y = s * (0.5 - fyc) * SCR.h;
	return (
		<div style={{position: 'absolute', inset: 0, overflow: 'hidden', backgroundColor: '#FFFFFF'}}>
			<div style={{position: 'absolute', inset: 0, transform: `translate3d(${x}px, ${y}px, 0) scale(${s})`}}>
				<Img src={staticFile(`shots/${shot}`)} style={{width: '100%', height: '100%', display: 'block'}} />
				{children}
			</div>
		</div>
	);
};
