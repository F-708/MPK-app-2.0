import React from 'react';
import {C, SceneProps} from '../theme';
import {SceneWrap, Stage} from '../components/Layout';
import {Shot} from '../components/Shot';

// A1 (0–60): фон #F4F6F8, мокап влетает по spring (scale .92→1, y+180→0, 550мс),
// наклон −2°, экран «Расписание, сегодня» (реальный shot_schedule_today, якорь cover по верху),
// титр «Мой Политех» opacity=1 уже на f000 (delay −12: fade −12..0, vision-QA2) + янтарная точка-пульс.
export const S01: React.FC<SceneProps> = ({have, wide}) => {
	return (
		<SceneWrap bg={C.bg}>
			<Stage
				wide={wide}
				entrance
				tilt={-2}
				screen={<Shot name="shot_schedule_today" have={have} style={{objectPosition: '50% 0%'}} />}
				title="Мой Политех"
				titleDot
				titleDelay={-12}
			/>
		</SceneWrap>
	);
};
