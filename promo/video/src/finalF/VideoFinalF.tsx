// PromoFinal9x16-d4 — master 30.0s = 900f @30fps. Границы 0/105/270/420/585/765/900.
import React from 'react';
import {AbsoluteFill, Audio, Sequence, interpolate, staticFile, useCurrentFrame} from 'remotion';
import {F1, F2, F3, F4, F5, F6} from './scenes';
import {BOUNDS, CF} from './theme';

const SCENES = [F1, F2, F3, F4, F5, F6];

export const VideoFinalF: React.FC = () => {
	const frame = useCurrentFrame();
	const black = interpolate(frame, [885, 897], [0, 1], {
		extrapolateLeft: 'clamp',
		extrapolateRight: 'clamp',
	});
	return (
		<AbsoluteFill style={{backgroundColor: CF.navy}}>
			{SCENES.map((Scene, i) => (
				<Sequence key={i} from={BOUNDS[i]} durationInFrames={BOUNDS[i + 1] - BOUNDS[i]}>
					<Scene />
				</Sequence>
			))}
			<AbsoluteFill style={{backgroundColor: '#000000', opacity: black, pointerEvents: 'none'}} />
			<Audio src={staticFile('audio/teaser_v3_30s_90bpm.wav')} />
		</AbsoluteFill>
	);
};
