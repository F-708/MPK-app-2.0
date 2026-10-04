import React from 'react';
import {Sequence, useVideoConfig} from 'remotion';
import {C, SceneProps} from '../theme';
import {SceneWrap, Stage} from '../components/Layout';
import {Shot} from '../components/Shot';
import {KenBurns} from '../components/KenBurns';
import {ChevronFlow} from '../components/ChevronFlow';
import {Callout} from '../components/Callout';

// A2 (60–180): карта — план этажа, маршрут с бегущими шевронами (оверлей с параметрами
// приложения), янтарный пин точки Б снаружи; врезка ~1с: конструктор «Откуда/Куда» (зум).
// Мокап +3°, пан вдоль маршрута за шевронами. Переход slide-left.
const ROUTE: [number, number][] = [
	[220, 1420],
	[220, 980],
	[520, 980],
	[520, 520],
];

export const S02: React.FC<SceneProps> = ({have, wide}) => {
	const {fps} = useVideoConfig();
	return (
		<SceneWrap bg={C.bg} in="slide-left">
			<Stage
				wide={wide}
				tilt={3}
				screen={
					<>
						<Sequence durationInFrames={84} premountFor={fps}>
							<KenBurns scale={[1.16, 1.26]} x={[10, -20]} y={[40, -70]}>
								<Shot name="a2_map_route" have={have} />
								<ChevronFlow points={ROUTE} />
							</KenBurns>
						</Sequence>
						<Sequence from={84} premountFor={fps}>
							<KenBurns scale={[1.02, 1.24]} x={[0, -50]} y={[0, -110]}>
								<Shot name="a2_map_constructor" have={have} />
							</KenBurns>
						</Sequence>
					</>
				}
				title="Заблудиться в колледже? Маршрут до кабинета — по коридорам и лестницам"
				accent="Маршрут до кабинета"
				overlay={
					<Sequence durationInFrames={84}>
						<Callout delay={18} x={124 + 520} y={84 + 520} lx={740} ly={38} label="точка Б" />
					</Sequence>
				}
			/>
		</SceneWrap>
	);
};
