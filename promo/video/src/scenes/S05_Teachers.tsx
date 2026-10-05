import React from 'react';
import {C, SceneProps} from '../theme';
import {SceneWrap, Stage} from '../components/Layout';
import {Shot} from '../components/Shot';
import {KenBurns} from '../components/KenBurns';
import {Callout} from '../components/Callout';

// A5 (420–510): преподаватели — ЗУМ-кроп реального shot_schedule_today на строке урока
// (вторая карточка списка: ФИО + чип кабинета), Ken Burns scale 1.15→1.6; старт-кадр
// (локальный 0 = f420): верхний кроп ≈0 — статусбар, навбар и орг-шапка целиком
// (vision-QA2, contain-посадка), к концу зума строка центрируется. Титр и обе выноски
// видны УЖЕ на локальном 0 (delay −10: fade-in завершён к старту сцены). Переход cut.
export const S05: React.FC<SceneProps> = ({have, wide}) => {
	// contain-маппинг: контент_y = img_y · (выс.экрана/1830); центр строки 2-й карточки
	// (img 726): 9:16 y≈660, 16:9 y≈354. Старт t = центр·(s−1) → нулевой кроп сверху;
	// финиш t = (центр экрана − центр строки)·1.6 → строка в центре.
	const zoomY = wide ? [66, 147] : [124, 275];
	return (
		<SceneWrap bg={C.bg}>
			<Stage
				wide={wide}
				screen={
					<KenBurns scale={[1.15, 1.6]} y={zoomY}>
						<Shot name="shot_schedule_today" have={have} style={{objectFit: 'contain', backgroundColor: C.bg}} />
					</KenBurns>
				}
				title="Кто ведёт пару и в каком кабинете"
				accent="в каком кабинете"
				titleDelay={-10}
				overlay={
					<>
						<Callout delay={-10} x={214} y={984} lx={300} ly={240} label="ФИО и «ведёт у вас»" />
						<Callout delay={-10} x={864} y={989} lx={680} ly={240} label="кабинет → расписание кабинета" />
					</>
				}
			/>
		</SceneWrap>
	);
};
