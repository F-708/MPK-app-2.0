import React from 'react';
import {useCurrentFrame, useVideoConfig} from 'remotion';
import {C, SceneProps} from '../theme';
import {SceneWrap, Stage} from '../components/Layout';
import {Shot} from '../components/Shot';
import {KenBurns, projectShot} from '../components/KenBurns';
import {Callout} from '../components/Callout';

// A5 (420–510): преподаватели — ЗУМ-кроп реального shot_schedule_today на строке урока
// (подсвеченная карточка: ФИО + чип кабинета), Ken Burns scale 1.15→1.6; старт-кадр
// (локальный 0 = f420): верхний кроп ≈0 — статусбар, навбар и орг-шапка целиком
// (vision-QA2, contain-посадка), к концу зума строка центрируется. Титр и обе выноски
// видны УЖЕ на локальном 0 (delay −10: fade-in завершён к старту сцены). Переход cut.
// Цели выносок измерены по пикселям shot_schedule_today.png (824×1830, PIL):
// текст «Иванова И.И.» bbox (58,1041)-(204,1057) → центр (131,1049);
// чип «каб. 118» bbox (631,1036)-(765,1064) → центр (698,1050).
// Кончики едут вместе с камерой через projectShot → попадание ±0px на любом кадре,
// в обеих ориентациях (vision-QA3: в портрете кончики отставали от целей).
export const S05: React.FC<SceneProps> = ({have, wide}) => {
	const frame = useCurrentFrame();
	const {durationInFrames: dur} = useVideoConfig();
	// contain-маппинг: контент_y = img_y · (выс.экрана/1830); старт t = центр·(s−1) →
	// нулевой кроп сверху; финиш t = (центр экрана − центр строки)·1.6 → строка в центре.
	const scale = [1.15, 1.6];
	const zoomY = wide ? [66, 147] : [124, 275];
	const at = projectShot({wide, iw: 824, ih: 1830, fit: 'contain', scale, y: zoomY}, frame, dur);
	const teacher = at(131, 1049);
	const room = at(698, 1050);
	return (
		<SceneWrap bg={C.bg}>
			<Stage
				wide={wide}
				screen={
					<KenBurns scale={scale} y={zoomY}>
						<Shot name="shot_schedule_today" have={have} style={{objectFit: 'contain', backgroundColor: C.bg}} />
					</KenBurns>
				}
				title="Кто ведёт пару и в каком кабинете"
				accent="в каком кабинете"
				titleDelay={-10}
				overlay={
					<>
						{/* лейблы НЕ в один ряд: левый над целью, правый под целью (vision-QA3: чипы
						    наезжали друг на друга и лежали на H1); maxW 340 — текст целиком, до 2 строк */}
						<Callout delay={-10} x={teacher.x} y={teacher.y} lx={300} ly={teacher.y - 90} maxW={340} label="ФИО и «ведёт у вас»" />
						<Callout
							delay={-10}
							x={room.x}
							y={room.y}
							lx={760}
							ly={Math.min(room.y + 60, 1408)}
							maxW={340}
							fs={24}
							label="кабинет → расписание кабинета"
						/>
					</>
				}
			/>
		</SceneWrap>
	);
};
