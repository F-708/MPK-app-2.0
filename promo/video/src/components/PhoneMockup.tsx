import React from 'react';
import {interpolate, spring, useCurrentFrame, useVideoConfig} from 'remotion';

// Мокап телефона — CSS-рамка по стилевой библии п.4.4 (без PNG-ассетов).
export const PhoneMockup: React.FC<{
	width?: number;
	aspect?: number; // высота экрана / ширина экрана
	tilt?: number; // наклон, deg
	entrance?: boolean; // spring-влет: scale .92→1, y +300→0, 550мс (п.4.5)
	children: React.ReactNode;
}> = ({width = 860, aspect = 2.0, tilt = 0, entrance = false, children}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const s = entrance
		? spring({frame, fps, config: {damping: 200, stiffness: 120}, durationInFrames: 17})
		: 1;

	const screenW = width - 28;
	return (
		<div
			style={{
				width: screenW,
				height: screenW * aspect,
				padding: 14,
				backgroundColor: '#111820',
				borderRadius: 56,
				boxSizing: 'content-box',
				boxShadow: '0 40px 80px rgba(11,53,100,0.25), 0 8px 16px rgba(0,0,0,0.12)',
				position: 'relative',
				rotate: `${tilt}deg`,
				scale: interpolate(s, [0, 1], [0.92, 1]),
				// y +180→0 (было +300): меньше обрезки низа мокапа на первом кадре (vision-QA f000).
				translate: `0 ${interpolate(s, [0, 1], [180, 0])}px`,
			}}
		>
			<div
				style={{
					width: '100%',
					height: '100%',
					borderRadius: 42,
					overflow: 'hidden',
					position: 'relative',
					backgroundColor: '#FFFFFF',
				}}
			>
				{children}
				{/* динамик-«таблетка» 140×28 */}
				<div
					style={{
						position: 'absolute',
						top: 18,
						left: '50%',
						translate: '-50% 0',
						width: 140,
						height: 28,
						borderRadius: 14,
						backgroundColor: '#111820',
						zIndex: 20,
					}}
				/>
			</div>
		</div>
	);
};
