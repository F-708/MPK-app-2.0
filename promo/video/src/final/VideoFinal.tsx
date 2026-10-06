import React from 'react';
import {AbsoluteFill, Series} from 'remotion';
import {S1Intro} from './S1Intro';
import {S2Schedule} from './S2Schedule';
import {S3Bells} from './S3Bells';
import {S4Map} from './S4Map';
import {S5Tasks} from './S5Tasks';
import {S6Outro} from './S6Outro';
import {C, SCENE_DUR} from './theme';

// PromoFinal9x16 — 30.0s = 900f @30fps. Границы: 0/105/270/420/585/765/900.
export const VideoFinal: React.FC = () => {
	const [d1, d2, d3, d4, d5, d6] = SCENE_DUR;
	return (
		<AbsoluteFill style={{backgroundColor: C.navyDeep}}>
			<Series>
				<Series.Sequence durationInFrames={d1}>
					<S1Intro />
				</Series.Sequence>
				<Series.Sequence durationInFrames={d2}>
					<S2Schedule />
				</Series.Sequence>
				<Series.Sequence durationInFrames={d3}>
					<S3Bells />
				</Series.Sequence>
				<Series.Sequence durationInFrames={d4}>
					<S4Map />
				</Series.Sequence>
				<Series.Sequence durationInFrames={d5}>
					<S5Tasks />
				</Series.Sequence>
				<Series.Sequence durationInFrames={d6}>
					<S6Outro />
				</Series.Sequence>
			</Series>
		</AbsoluteFill>
	);
};
