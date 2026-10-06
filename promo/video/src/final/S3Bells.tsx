import React from 'react';
import {useCurrentFrame} from 'remotion';
import {PulseDot} from './Overlay';
import {SceneShell} from './SceneShell';

export const S3Bells: React.FC<{dur?: number}> = ({dur = 150}) => {
	const frame = useCurrentFrame();
	return (
		<SceneShell
			dur={dur}
			label="ОТСЧЁТ ДО ЗВОНКА"
			line="Точное время до перемены на экране и в шторке"
			shot="shot_bells.png"
			scale={[1, 1.32]}
			fx={[0.5, 0.88]}
			fy={[0.42, 0.45]}
			overlays={<PulseDot fx={0.971} fy={0.462} frame={frame} period={45} />}
		/>
	);
};
