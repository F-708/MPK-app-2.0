// PromoFinal9x16_d4 — master 30.0s = 900f @30fps. Границы 0/105/270/420/585/765/900.
import React from 'react';
import {AbsoluteFill, Audio, Sequence, staticFile, useCurrentFrame} from 'remotion';
import {interpolate} from 'remotion';
import {F1, F2, F3, F4, F5, F6} from './scenes';
import {CF} from './theme';

export const VideoFinalF: React.FC = () => {
	const frame = useCurrentFrame();
	const black = interpolate(frame, [885, 900], [0, 1], {
		extrapolateLeft: 'clamp',
		extrapolateRight: 'clamp',
	});
	return (
		<AbsoluteFill style={{backgroundColor: CF.navy}}>
			<Sequence durationInFrames={105}>
				<F1 />
			</Sequence>
			<Sequence from={105} durationInFrames={165}>
				<F2 />
			</Sequence>
			<Sequence from={270} durationInFrames={150}>
				<F3 />
			</Sequence>
			<Sequence from={420} durationInFrames={165}>
				<F4 />
			</Sequence>
			<Sequence from={585} durationInFrames={180}>
				<F5 />
			</Sequence>
			<Sequence from={765} durationInFrames={135}>
				<F6 />
			</Sequence>
			<AbsoluteFill style={{backgroundColor: '#000000', opacity: black, pointerEvents: 'none'}} />
			<Audio src={staticFile('audio/teaser_v3_30s_90bpm.wav')} />
		</AbsoluteFill>
	);
};
