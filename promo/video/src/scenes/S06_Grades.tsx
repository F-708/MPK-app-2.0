import React from 'react';
import {C, SceneProps} from '../theme';
import {SceneWrap, Stage} from '../components/Layout';
import {Shot} from '../components/Shot';
import {KenBurns} from '../components/KenBurns';
import {Callout} from '../components/Callout';

// A6 (510–600): оценки — нампад, тап «5», средний пересчитался мгновенно.
// Камера: зум на нампад → пан к среднему. Янтарная выноска снаружи на средний.
// Веса не упоминаем. Переход к A7 — slide-up (вход S07).
export const S06: React.FC<SceneProps> = ({have, wide}) => {
	return (
		<SceneWrap bg={C.bg}>
			<Stage
				wide={wide}
				screen={
					<KenBurns scale={[1.04, 1.22, 1.12]} x={[0, -25, 35]} y={[0, 150, -90]}>
						<Shot name="a6_grades" have={have} />
					</KenBurns>
				}
				title="Оценки и средний балл — без калькулятора"
				accent="без калькулятора"
				overlay={<Callout delay={48} x={124 + 620} y={84 + 420} lx={700} ly={232} label="средний балл" />}
			/>
		</SceneWrap>
	);
};
