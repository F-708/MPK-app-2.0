import React from 'react';
import {interpolate, spring, useCurrentFrame, useVideoConfig} from 'remotion';

// Моккап: тонкий тёмный безель, punch-hole фронталка, глубинная тень (бриф Фазы 2).
// Вход — пружинное всплытие снизу: spring(damping 14, mass 0.8).
export const Phone: React.FC<{
	width?: number;
	entrance?: boolean;
	children: React.ReactNode;
}> = ({width = 700, entrance = false, children}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const sp = entrance ? spring({frame, fps, config: {damping: 14, mass: 0.8}}) : 1;
	const bez = 10;
	const screenW = width - bez * 2;
	const screenH = screenW * (3660 / 1648);
	return (
		<div
			style={{
				width: screenW,
				height: screenH,
				padding: bez,
				backgroundColor: '#0D1524',
				borderRadius: 26,
				boxSizing: 'content-box',
				boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.35)',
				position: 'relative',
				scale: `${interpolate(sp, [0, 1], [0.88, 1])}`,
				translate: `0 ${interpolate(sp, [0, 1], [560, 0])}px`,
			}}
		>
			<div
				style={{
					width: '100%',
					height: '100%',
					borderRadius: 16,
					overflow: 'hidden',
					position: 'relative',
					backgroundColor: '#FFFFFF',
				}}
			>
				{children}
				{/* вырез фронтальной камеры */}
				<div
					style={{
						position: 'absolute',
						top: 22,
						left: '50%',
						translate: '-50% 0',
						width: 24,
						height: 24,
						borderRadius: 12,
						backgroundColor: '#0D1524',
						boxShadow: 'inset 0 0 0 3px rgba(255,255,255,0.06), 0 1px 2px rgba(0,0,0,0.4)',
						zIndex: 20,
					}}
				/>
			</div>
		</div>
	);
};
