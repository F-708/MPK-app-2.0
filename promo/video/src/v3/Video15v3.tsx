// Promo15v3 — cut-down 15.0s = 450f: S1 0–140, S3 140–335 (195f, ambient растянут),
// S7 335–450 (hold 36f: pushInStart 17) + fade 438–450. Те же компоненты, props-смещение кадров.
import React from 'react';
import {AbsoluteFill, Audio, Series, staticFile, useCurrentFrame} from 'remotion';
import {clamp01} from './anim3';
import {CaptionTrack} from './Caption3';
import {V3S1, V3S3, V3S7} from './scenes';
import {C3} from './theme3';

export const Video15v3: React.FC = () => {
	const frame = useCurrentFrame();
	return (
		<AbsoluteFill style={{backgroundColor: C3.bg}}>
			<Series>
				<Series.Sequence durationInFrames={140}>
					<V3S1 />
				</Series.Sequence>
				<Series.Sequence durationInFrames={195}>
					<V3S3 dur={195} />
				</Series.Sequence>
				<Series.Sequence durationInFrames={115}>
					<V3S7 pushInStart={17} />
				</Series.Sequence>
			</Series>
			<CaptionTrack
				frame={frame}
				items={[
					{text: '07:58', inF: 5, outF: 145},
					{text: '11:00', inF: 145, outF: 340},
				]}
			/>
			<AbsoluteFill
				style={{
					backgroundColor: C3.white,
					opacity: Math.max(1 - clamp01(frame / 8), clamp01((frame - 438) / 12)),
					pointerEvents: 'none',
				}}
			/>
			<Audio src={staticFile('audio/teaser_v3_30s_90bpm.wav')} />
		</AbsoluteFill>
	);
};
