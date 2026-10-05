import React from 'react';
import {Audio, Series, staticFile} from 'remotion';
import {Push, Stage15} from './Stage15';
import {S15_1} from './scenes/S15_1';
import {S15_2} from './scenes/S15_2';
import {S15_3} from './scenes/S15_3';
import {S15_4} from './scenes/S15_4';
import {S15_5} from './scenes/S15_5';
import {S15_6} from './scenes/S15_6';
import {S15_7} from './scenes/S15_7';

// Promo15 — v2: 15.0s, 450 кадров, 100BPM beat=18f. Границы сцен ТОЛЬКО по битам:
// 0–18–90–162–234–306–378–450. Склеек fade нет (кроме финала), вход сцен — hard-cut/push 4f.
// Stage15 снаружи Series: фон и дрейф сетки непрерывны через все склейки.
export const Video15: React.FC = () => (
	<>
		<Stage15>
			<Series>
				<Series.Sequence name="S1 wordmark" durationInFrames={18}>
					<S15_1 />
				</Series.Sequence>
				<Series.Sequence name="S2 schedule" durationInFrames={72}>
					<Push>
						<S15_2 />
					</Push>
				</Series.Sequence>
				<Series.Sequence name="S3 map" durationInFrames={72}>
					<Push>
						<S15_3 />
					</Push>
				</Series.Sequence>
				<Series.Sequence name="S4 bells" durationInFrames={72}>
					<Push>
						<S15_4 />
					</Push>
				</Series.Sequence>
				<Series.Sequence name="S5 tri-cut" durationInFrames={72}>
					<Push>
						<S15_5 />
					</Push>
				</Series.Sequence>
				<Series.Sequence name="S6 home" durationInFrames={72}>
					<Push>
						<S15_6 />
					</Push>
				</Series.Sequence>
				<Series.Sequence name="S7 finale" durationInFrames={72}>
					<S15_7 />
				</Series.Sequence>
			</Series>
		</Stage15>
		{/* В треке: intro 0–1.2s, грув 1.2–12.6, dip 12.6–13.4, аккорд 13.4–15.0 */}
		<Audio src={staticFile('audio/teaser_v2_15s.wav')} />
	</>
);
