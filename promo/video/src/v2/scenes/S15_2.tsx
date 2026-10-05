import React from 'react';
import {AbsoluteFill, Easing, interpolate, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {KineticTitle} from '../KineticTitle';
import {PhoneDark, ScreenImg} from '../PhoneDark';

// S2 (18–90) Расписание: телефон slide-up spring (10 кадров) справа (62% ширины);
// shot_schedule_today contain якорь верх; на бите 36 (кадр 18 сцены) swipe двух шотов:
// старый уезжает влево 8 кадров, новый приезжает справа; glass sweep 24–36 (кадры 6–18).
// Текст слева: «Расписание.» + акцент «сегодня и завтра» underline.
export const S15_2: React.FC = () => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const enter = spring({frame, fps, config: {damping: 14}, durationInFrames: 10});
	const swipe = interpolate(frame, [18, 26], [0, 100], {
		extrapolateLeft: 'clamp',
		extrapolateRight: 'clamp',
		easing: Easing.inOut(Easing.quad),
	});
	return (
		<AbsoluteFill>
			<PhoneDark scale={0.52} left={539} top={620} sweepFrom={6} style={{translate: `0 ${(1 - enter) * 120}px`}}>
				<ScreenImg name="shot_schedule_today" style={{translate: `${-swipe}%`}} />
				{frame >= 18 && <ScreenImg name="shot_schedule_tomorrow" style={{translate: `${100 - swipe}%`}} />}
			</PhoneDark>
			{/* текст в верхней зоне слева: при 84px заголовок шире боковой колонки */}
			<KineticTitle title="Расписание." accent="сегодня и завтра" titleAt={0} accentAt={18} left={96} top={260} width={540} />
		</AbsoluteFill>
	);
};
