import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame} from 'remotion';
import {KineticTitle} from '../KineticTitle';
import {PhoneDark, ScreenImg} from '../PhoneDark';

// S3 (90–162) Карта: телефон слева, текст справа; shot_map_route cover якорь центр маршрута;
// камера push-in scale 1.0→1.18 вдоль линии маршрута (pan вниз); родные шевроны видны —
// никаких оверлей-шевронов (запрет v1).
export const S15_3: React.FC = () => {
	const frame = useCurrentFrame();
	const s = interpolate(frame, [0, 72], [1.0, 1.18], {extrapolateRight: 'clamp'});
	const pan = interpolate(frame, [0, 72], [0, -46], {extrapolateRight: 'clamp'});
	return (
		<AbsoluteFill>
			<PhoneDark scale={0.52} left={96} top={561}>
				<div style={{position: 'absolute', inset: 0, overflow: 'hidden'}}>
					<div style={{position: 'absolute', inset: 0, transform: `scale(${s}) translateY(${pan}px)`}}>
						<ScreenImg name="shot_map_route" fit="cover" position="50% 45%" />
					</div>
				</div>
			</PhoneDark>
			<KineticTitle
				title="Маршрут"
				accent="до кабинета"
				sub="по коридорам и лестницам"
				titleAt={0}
				accentAt={18}
				left={584}
				top={620}
				width={400}
			/>
		</AbsoluteFill>
	);
};
