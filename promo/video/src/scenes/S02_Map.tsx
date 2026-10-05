import React from 'react';
import {Sequence, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, Golos, SceneProps} from '../theme';
import {SceneWrap, Stage} from '../components/Layout';
import {Shot} from '../components/Shot';
import {KenBurns} from '../components/KenBurns';
import {Callout} from '../components/Callout';

// A2 (60–180): карта — план этажа с РОДНЫМИ шевронами скриншота (SVG-оверлей удалён,
// vision-QA f120); движение = медленный Ken Burns-пан вдоль линии маршрута сверху вниз
// (scale 1.05→1.12), якорь cover +60px вниз (строка «ОТМЕНИТЬ МАРШРУТ» целиком под таблеткой);
// врезка 1 с (84–114): карточка-конструктор А→Б поверх кадра (видео-графика, не часть экрана).
// Мокап +3°. Переход slide-left.

// Реплика RoutePairPanel: два поля с placeholder + кнопка ПОСТРОИТЬ #0B3564.
// Скругления 2dp (≈5px кадра), тень.
const RoutePairCard: React.FC = () => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const pop = spring({frame, fps, config: {damping: 150, stiffness: 200}, durationInFrames: 8});
	const field: React.CSSProperties = {
		height: 88,
		border: '2px solid #D5DEE8',
		borderRadius: 5,
		padding: '0 24px',
		display: 'flex',
		alignItems: 'center',
		fontFamily: Golos,
		fontWeight: 400,
		fontSize: 32,
		color: '#8E9BAA',
		backgroundColor: '#FBFCFD',
	};
	return (
		<div
			style={{
				position: 'absolute',
				left: 190,
				top: 760,
				width: 700,
				backgroundColor: '#FFFFFF',
				borderRadius: 5,
				boxShadow: '0 24px 60px rgba(0,0,0,0.30)',
				padding: 26,
				display: 'flex',
				flexDirection: 'column',
				gap: 18,
				opacity: pop,
				scale: `${0.9 + 0.1 * pop}`,
			}}
		>
			<div style={field}>Откуда (пусто = вход)</div>
			<div style={field}>Куда</div>
			<div
				style={{
					height: 92,
					backgroundColor: C.navy,
					borderRadius: 5,
					display: 'flex',
					alignItems: 'center',
					justifyContent: 'center',
					fontFamily: Golos,
					fontWeight: 700,
					fontSize: 34,
					letterSpacing: 1,
					color: '#FFFFFF',
				}}
			>
				ПОСТРОИТЬ
			</div>
		</div>
	);
};

export const S02: React.FC<SceneProps> = ({have, wide}) => {
	return (
		<SceneWrap bg={C.bg} in="slide-left">
			<Stage
				wide={wide}
				tilt={3}
				screen={
					<KenBurns scale={[1.05, 1.12]} x={[0, -10]} y={[0, -90]}>
						{/* якорь cover по низу + пан вверх: строка «ОТМЕНИТЬ МАРШРУТ» уходит под таблетку целиком (vision-QA f120) */}
						<Shot name="a2_map_route" have={have} style={{objectPosition: '50% 100%'}} />
					</KenBurns>
				}
				title="Заблудиться в колледже?"
				sub="Маршрут до кабинета — по коридорам и лестницам"
				accent="Маршрут до кабинета"
				overlay={
					<>
						{/* кончик стрелки — финиш родного маршрута у лестницы (vision-QA f120) */}
						<Sequence durationInFrames={84}>
							<Callout delay={18} x={540} y={1400} lx={760} ly={232} label="точка Б" />
						</Sequence>
						<Sequence from={84} durationInFrames={30}>
							<RoutePairCard />
						</Sequence>
					</>
				}
			/>
		</SceneWrap>
	);
};
