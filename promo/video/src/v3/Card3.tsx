// Card3 — crop-контейнер шота: Img objectFit cover + objectPosition по crop-долям.
// Контейнер держит аспект crop-прямоугольника, тогда cover+objectPosition показывают его точно:
// objectPosition% = c0/(1-span) по каждой оси (при span=1 — 50%, overflow=0).
import React from 'react';
import {Img, staticFile} from 'remotion';
import {clamp01, easeInOut, spr} from './anim3';
import {CARD_SHADOW, CARD_TOP, CARD_W, C3, RADIUS, SHOT_H, SHOT_W} from './theme3';

export type Crop = [number, number, number, number]; // x0,x1,y0,y1 — доли от размера шота

export const cropAspect = (c: Crop) => ((c[1] - c[0]) * SHOT_W) / ((c[3] - c[2]) * SHOT_H);
export const cardHeight = (c: Crop) => CARD_W / cropAspect(c);
const pos = (c0: number, span: number) => (span >= 1 ? 50 : (c0 / (1 - span)) * 100);

export const Card3: React.FC<{
	shot: string;
	crop: Crop;
	frame: number;
	fps: number;
	dur: number;
	enter: 'open' | 'push'; // open: spring ζ.9 15f scale.94/y+60/blur10 + rotateY6→0 20f (только S1)
	out?: boolean; // cross-push выход на последних 5f сцены
	contentStyle?: React.CSSProperties; // zoom/scroll поверх Img
	children?: React.ReactNode; // оверлеи в координатах карточки
}> = ({shot, crop, frame, fps, dur, enter, out = true, contentStyle, children}) => {
	const [x0, x1, y0, y1] = crop;
	const h = cardHeight(crop);

	// вход open (S1): scale .94→1, y+60→0, blur10→0, spring ζ0.9 15f
	const sOpen = enter === 'open' ? spr(frame, fps, 0.9, 15) : 1;
	const rotY = enter === 'open' ? 6 * (1 - easeInOut(frame / 20)) : 0;
	// выход: −6%x/−4%y, scale .94, blur 6, opacity→0 на последних 4f
	const tOut = out && frame > dur - 5 ? (frame - (dur - 5)) / 5 : 0;
	const opacity = out && frame > dur - 4 ? clamp01((dur - frame) / 4) : 1;

	const style: React.CSSProperties = {
		position: 'absolute',
		left: (1080 - CARD_W) / 2,
		top: CARD_TOP,
		width: CARD_W,
		height: h,
		borderRadius: RADIUS,
		opacity,
		transform:
			enter === 'open'
				? `perspective(1400px) rotateY(${rotY}deg) translateY(${60 * (1 - sOpen)}px) scale(${0.94 + 0.06 * sOpen}) translate(${tOut ? -6 * tOut : 0}%, ${tOut ? -4 * tOut : 0}%) scale(${1 - 0.06 * tOut})`
				: `translate(${-6 * tOut}%, ${-4 * tOut}%) scale(${1 - 0.06 * tOut})`,
		filter: [
			enter === 'open' && frame < 15 ? `blur(${10 * (1 - sOpen)}px)` : '',
			tOut ? `blur(${6 * tOut}px)` : '',
		]
			.filter(Boolean)
			.join(' '),
	};

	return (
		<div style={style}>
			<div
				style={{
					position: 'absolute',
					inset: 0,
					borderRadius: RADIUS,
					overflow: 'hidden',
					border: `1px solid ${C3.hairline}`,
					boxShadow: CARD_SHADOW,
					backgroundColor: C3.white,
				}}
			>
				<div style={{position: 'absolute', inset: 0, ...contentStyle}}>
					<Img
						src={staticFile(`shots/${shot}.png`)}
						style={{
							width: '100%',
							height: '100%',
							objectFit: 'cover',
							objectPosition: `${pos(x0, x1 - x0)}% ${pos(y0, y1 - y0)}%`,
						}}
					/>
					{children}
				</div>
			</div>
		</div>
	);
};
