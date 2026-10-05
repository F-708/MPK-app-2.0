// Stage3 — фон 4 слоя + grain + гигантская цифра времени + сетка маршрутов.
import React from 'react';
import {C3, H, Mono3, W} from './theme3';

const GRAIN =
	'url("data:image/svg+xml;utf8,<svg xmlns=\'http://www.w3.org/2000/svg\' width=\'240\' height=\'240\'><filter id=\'n\'><feTurbulence type=\'fractalNoise\' baseFrequency=\'0.9\' numOctaves=\'2\' stitchTiles=\'stitch\'/><feColorMatrix type=\'saturate\' values=\'0\'/></filter><rect width=\'240\' height=\'240\' filter=\'url(%23n)\'/></svg>")';

// Ортогональная ломаная-«коридор» в свободной от карточки зоне (низ кадра).
const GRID_PTS = '20,40 140,40 140,120 300,120 300,60 440,60 440,180 240,180 240,230 460,230';

export const Stage3: React.FC<{
	digit?: string | null;
	digitSide?: 'left' | 'right';
	children?: React.ReactNode;
}> = ({digit, digitSide = 'left', children}) => (
	<div style={{position: 'absolute', inset: 0, backgroundColor: C3.bg, overflow: 'hidden'}}>
		{/* radial #E8F0FE центр 30%/20% r1100 a.70 */}
		<div
			style={{
				position: 'absolute',
				inset: 0,
				background: `radial-gradient(1100px 1100px at 30% 20%, ${C3.radial} 0%, rgba(232,240,254,0) 100%)`,
				opacity: 0.7,
			}}
		/>
		{/* linear-cool с y70% */}
		<div
			style={{
				position: 'absolute',
				inset: 0,
				background: `linear-gradient(to bottom, rgba(11,53,100,0) 70%, ${C3.cool} 100%)`,
			}}
		/>
		{/* сетка маршрутных линий — только внизу, вне карточки */}
		<svg
			width={480}
			height={250}
			viewBox="0 0 480 250"
			style={{
				position: 'absolute',
				bottom: 30,
				[digitSide === 'left' ? 'right' : 'left']: 40,
				opacity: 0.08,
				filter: 'blur(1px)',
			}}
		>
			<polyline points={GRID_PTS} fill="none" stroke={C3.blue} strokeWidth={2} />
			<polyline points="60,230 60,90 200,90 200,20 460,20" fill="none" stroke={C3.blue} strokeWidth={2} />
		</svg>
		{/* гигантская цифра времени сцены, bleed-cut кромкой, z между фоном и карточкой */}
		{digit ? (
			<div
				style={{
					position: 'absolute',
					bottom: -150,
					[digitSide === 'left' ? 'left' : 'right']: -30,
					fontFamily: Mono3,
					fontWeight: 700,
					fontSize: 420,
					lineHeight: 1,
					color: C3.blue,
					opacity: 0.06,
					whiteSpace: 'nowrap',
				}}
			>
				{digit}
			</div>
		) : null}
		{children}
		{/* grain mono 2.5% поверх ВСЕГО */}
		<div
			style={{
				position: 'absolute',
				inset: 0,
				backgroundImage: GRAIN,
				opacity: 0.025,
				pointerEvents: 'none',
			}}
		/>
	</div>
);

export const STAGE_SIZE = {W, H};
