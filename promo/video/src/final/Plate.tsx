// Plate — оверлей сцены: микро-лейбл UPPERCASE 0.15em + нейтральный заголовок.
import React from 'react';
import {Easing, interpolate, useCurrentFrame, useVideoConfig} from 'remotion';
import {CF, DEPTH_SHADOW, F, R2} from './theme';

export const Plate: React.FC<{label: string; text: string; delay?: number}> = ({
	label,
	text,
	delay = 12,
}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	if (frame < delay) return null;
	const t = interpolate(frame - delay, [0, 0.4 * fps], [0, 1], {
		extrapolateLeft: 'clamp',
		extrapolateRight: 'clamp',
		easing: Easing.out(Easing.cubic),
	});
	return (
		<div
			style={{
				position: 'absolute',
				left: 72,
				right: 72,
				bottom: 110,
				zIndex: 5,
				opacity: t,
				translate: `0 ${(1 - t) * 28}px`,
				backgroundColor: CF.surface,
				border: `1px solid ${CF.border}`,
				borderRadius: R2,
				padding: '26px 34px',
				boxShadow: DEPTH_SHADOW,
			}}
		>
			<div style={{fontFamily: F, fontWeight: 700, fontSize: 30, letterSpacing: '0.15em', color: CF.accent}}>
				{label}
			</div>
			<div style={{fontFamily: F, fontWeight: 500, fontSize: 40, lineHeight: 1.25, color: CF.title, marginTop: 8}}>
				{text}
			</div>
		</div>
	);
};
