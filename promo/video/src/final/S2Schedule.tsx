import React from 'react';
import {useCurrentFrame} from 'remotion';
import {FocusBox} from './Overlay';
import {SceneShell} from './SceneShell';

// Координаты фокуса — доли экрана; калибруются по QA-кадрам (f0170/f0260).
export const S2Schedule: React.FC<{dur?: number}> = ({dur = 165}) => {
	const frame = useCurrentFrame();
	return (
		<SceneShell
			dur={dur}
			label="РАСПИСАНИЕ"
			line="Пары, аудитории и подгруппы офлайн"
			shot="shot_schedule_today.png"
			scale={[1, 1.3]}
			fx={[0.5, 0.5]}
			fy={[0.42, 0.28]}
			overlays={<FocusBox fx={0.5} fy={0.28} w={0.9} h={0.14} frame={frame} at={65} />}
		/>
	);
};
