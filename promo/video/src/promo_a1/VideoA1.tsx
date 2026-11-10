import React from 'react';
import {Series} from 'remotion';
import {S1Hook, S2Today, S3Countdown, S4Bells, S5Outro} from './scenes';

// PromoA1 — изолированная 30с линия про основную функцию «Расписание».
// 900 кадров @30fps. Тайминги: 0-120 хук, 120-420 сегодня, 420-600 до звонка,
// 600-780 звонки, 780-900 аутро.
export const VideoA1: React.FC = () => {
	return (
		<Series>
			<Series.Sequence durationInFrames={120}>
				<S1Hook />
			</Series.Sequence>
			<Series.Sequence durationInFrames={300}>
				<S2Today />
			</Series.Sequence>
			<Series.Sequence durationInFrames={180}>
				<S3Countdown />
			</Series.Sequence>
			<Series.Sequence durationInFrames={180}>
				<S4Bells />
			</Series.Sequence>
			<Series.Sequence durationInFrames={120}>
				<S5Outro />
			</Series.Sequence>
		</Series>
	);
};
