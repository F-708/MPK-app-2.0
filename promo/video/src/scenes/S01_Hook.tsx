import React from 'react';
import {C, SceneProps} from '../theme';
import {SceneWrap, Stage} from '../components/Layout';
import {Shot} from '../components/Shot';

// A1 (0–60): фон #F4F6F8, мокап влетает по spring (scale .92→1, y+300→0, 550мс),
// наклон −2°, экран «Расписание, сегодня», титр «Мой Политех» + янтарная точка-пульс.
export const S01: React.FC<SceneProps> = ({have, wide}) => {
	return (
		<SceneWrap bg={C.bg}>
			<Stage
				wide={wide}
				entrance
				tilt={-2}
				screen={<Shot name="a1_schedule_today" have={have} />}
				title="Мой Политех"
				titleDot
			/>
		</SceneWrap>
	);
};
