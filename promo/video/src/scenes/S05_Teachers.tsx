import React from 'react';
import {Sequence, useVideoConfig} from 'remotion';
import {C, SceneProps} from '../theme';
import {SceneWrap, Stage} from '../components/Layout';
import {Shot} from '../components/Shot';
import {KenBurns} from '../components/KenBurns';
import {Callout} from '../components/Callout';

// A5 (420–510): преподаватели — список ФИО (пан вниз) → карточка «ведёт у вас»
// (зум в карточку), янтарная выноска на «ведёт у вас». Переход cut.
export const S05: React.FC<SceneProps> = ({have, wide}) => {
	const {fps} = useVideoConfig();
	return (
		<SceneWrap bg={C.bg}>
			<Stage
				wide={wide}
				screen={
					<>
						<Sequence durationInFrames={45} premountFor={fps}>
							<KenBurns scale={[1.16, 1.16]} y={[-90, 90]}>
								<Shot name="a5_teachers_list" have={have} />
							</KenBurns>
						</Sequence>
						<Sequence from={45} premountFor={fps}>
							<KenBurns scale={[1.06, 1.16]} y={[30, -40]}>
								<Shot name="a5_teacher_card" have={have} />
							</KenBurns>
						</Sequence>
					</>
				}
				title="Кто ведёт пару и в каком кабинете"
				accent="в каком кабинете"
				overlay={
					<Sequence from={45}>
						<Callout delay={12} x={124 + 416} y={84 + 560} lx={640} ly={38} label="ведёт у вас" />
					</Sequence>
				}
			/>
		</SceneWrap>
	);
};
