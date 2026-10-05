// Promo30 — master 30.0s = 900f @30fps 1080×1920. Границы 0/140/260/375/485/650/785/900.
import React from 'react';
import {AbsoluteFill, Audio, Series, staticFile, useCurrentFrame} from 'remotion';
import {clamp01} from './anim3';
import {CaptionTrack} from './Caption3';
import copy from './copy.json';
import {V3S1, V3S2, V3S3, V3S4, V3S5, V3S6, V3S7} from './scenes';
import {C3} from './theme3';

export const Video30: React.FC = () => {
	const frame = useCurrentFrame();
	const B = copy.bounds;
	const captions = copy.scenes
		.map((s, i) => (s.caption ? {text: s.caption, inF: B[i] + 5, outF: B[i + 1] + 5} : null))
		.filter(Boolean) as {text: string; inF: number; outF: number}[];
	return (
		<AbsoluteFill style={{backgroundColor: C3.bg}}>
			<Series>
				<Series.Sequence durationInFrames={140}>
					<V3S1 />
				</Series.Sequence>
				<Series.Sequence durationInFrames={120}>
					<V3S2 />
				</Series.Sequence>
				<Series.Sequence durationInFrames={115}>
					<V3S3 />
				</Series.Sequence>
				<Series.Sequence durationInFrames={110}>
					<V3S4 />
				</Series.Sequence>
				<Series.Sequence durationInFrames={165}>
					<V3S5 />
				</Series.Sequence>
				<Series.Sequence durationInFrames={135}>
					<V3S6 />
				</Series.Sequence>
				<Series.Sequence durationInFrames={115}>
					<V3S7 />
				</Series.Sequence>
			</Series>
			<CaptionTrack frame={frame} items={captions} />
			{/* open: fade из белого 0–8f; close: fade в белый 888–900f */}
			<AbsoluteFill
				style={{
					backgroundColor: C3.white,
					opacity: Math.max(1 - clamp01(frame / 8), clamp01((frame - 888) / 12)),
					pointerEvents: 'none',
				}}
			/>
			<Audio src={staticFile('audio/teaser_v3_30s_90bpm.wav')} />
		</AbsoluteFill>
	);
};
