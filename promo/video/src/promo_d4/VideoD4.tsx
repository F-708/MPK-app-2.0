import React from 'react';
import {Series} from 'remotion';
import {S1Hook, S2HomeScreen, S3Countdown, S4TodayWidget, S5Outro} from './scenes';

// PromoD4 — 30с линия «Виджеты и звонки».
// 900 кадров @30fps, 9x16 (1080x1920).
// Тайминги: 0-120 хук, 120-360 домашний экран, 360-570 таймер до звонка,
// 570-780 виджет «сегодня», 780-900 аутро.
export const VideoD4: React.FC = () => {
	return (
		<Series>
			<Series.Sequence durationInFrames={120}>
				<S1Hook />
			</Series.Sequence>
			<Series.Sequence durationInFrames={240}>
				<S2HomeScreen />
			</Series.Sequence>
			<Series.Sequence durationInFrames={210}>
				<S3Countdown />
			</Series.Sequence>
			<Series.Sequence durationInFrames={210}>
				<S4TodayWidget />
			</Series.Sequence>
			<Series.Sequence durationInFrames={120}>
				<S5Outro />
			</Series.Sequence>
		</Series>
	);
};
