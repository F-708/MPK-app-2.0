import React from 'react';
import {C, SceneProps} from '../theme';
import {SceneWrap, Stage} from '../components/Layout';
import {Shot} from '../components/Shot';
import {KenBurns} from '../components/KenBurns';
import {Callout} from '../components/Callout';

// A5 (420–510): преподаватели — ЗУМ-кроп реального shot_schedule_today на строке урока
// (вторая карточка списка: ФИО + чип кабинета), Ken Burns scale 1.0→1.6 к центру строки
// (translatе компенсирует зум, чтобы строка держалась в центре); две выноски снаружи:
// «ФИО и «ведёт у вас»» и «кабинет → расписание кабинета» (vision-QA f420). Переход cut.
export const S05: React.FC<SceneProps> = ({have, wide}) => {
	// центр строки 2-й карточки в экранных координатах мокапа: 9:16 y≈642, 16:9 y≈351;
	// translate = (центр экрана − центр строки) · scale держит строку в центре кадра.
	const zoomY = wide ? [0, 153] : [0, 304];
	return (
		<SceneWrap bg={C.bg}>
			<Stage
				wide={wide}
				screen={
					<KenBurns scale={[1, 1.6]} y={zoomY}>
						<Shot name="shot_schedule_today" have={have} />
					</KenBurns>
				}
				title="Кто ведёт пару и в каком кабинете"
				accent="в каком кабинете"
				overlay={
					<>
						<Callout delay={30} x={214} y={984} lx={300} ly={240} label="ФИО и «ведёт у вас»" />
						<Callout delay={45} x={864} y={989} lx={680} ly={240} label="кабинет → расписание кабинета" />
					</>
				}
			/>
		</SceneWrap>
	);
};
