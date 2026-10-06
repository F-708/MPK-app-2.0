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
			line="Точное время до перемены на экране и в шорке"
			shot="shot_bells.png"
			scale={[1, 1.32]}
			fx={[0.5, 0.5]}
			fy={[0.4, 0.3]}
			overlays={<PulseDot fx={0.86} fy={0.3} frame={frame} period={45} />}
		/>
	);
};
