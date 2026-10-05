import React from 'react';
import {useCurrentFrame, useVideoConfig} from 'remotion';
import {C, SceneProps} from '../theme';
import {SceneWrap, Stage} from '../components/Layout';
import {Shot} from '../components/Shot';
import {KenBurns, projectShot} from '../components/KenBurns';
import {Callout} from '../components/Callout';

// A6 (510–600): оценки — список предметов, у «Математики» средний 4.2 справа.
// Камера: лёгкий зум + пан вниз к строке со средним, к f560 (локальный 50) окно стоит
// центрированно по ШИРИНЕ списка (objectPosition 50%): имена слева (ix 51) и «4.2»
// справа (ix 715-773) в кадре (vision-QA3: кроп уезжал вправо, средний не виден).
// Ключи подобраны так, что на локальном 50 scale≈1.102, x=0, y≈−182.
// Средний измерен по пикселям shot_grades.png (824×1830, PIL): «4.2» bbox
// (715,1066)-(773,1096) → центр (744,1081); кончик выноски едет с камерой (projectShot).
export const S06: React.FC<SceneProps> = ({have, wide}) => {
	const frame = useCurrentFrame();
	const {durationInFrames: dur} = useVideoConfig();
	const scale = [1.02, 1.1, 1.12];
	const panX = [0, 0, 0];
	const panY = [0, -183, -175];
	const at = projectShot({wide, iw: 824, ih: 1830, fit: 'cover', scale, x: panX, y: panY}, frame, dur);
	const avg = at(744, 1081);
	return (
		<SceneWrap bg={C.bg}>
			<Stage
				wide={wide}
				screen={
					<KenBurns scale={scale} x={panX} y={panY}>
						<Shot name="a6_grades" have={have} />
					</KenBurns>
				}
				title="Оценки и средний балл — без калькулятора"
				accent="без калькулятора"
				overlay={<Callout delay={42} x={avg.x} y={avg.y} lx={850} ly={avg.y - 120} label="средний балл" />}
			/>
		</SceneWrap>
	);
};
