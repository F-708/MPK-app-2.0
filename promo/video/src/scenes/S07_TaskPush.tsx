import React from 'react';
import {Sequence, useVideoConfig} from 'remotion';
import {C, SceneProps} from '../theme';
import {SceneWrap, Stage} from '../components/Layout';
import {Shot} from '../components/Shot';
import {KenBurns} from '../components/KenBurns';
import {Callout} from '../components/Callout';
import {HomeScreen, NotificationCard, Shade} from '../components/AndroidChrome';

// A7 (600–690): задания — «Добавить», текст, напоминание (крупный план диалога,
// янтарная выноска на поле напоминания); match-cut в пуш-шторку с РЕАЛЬНОЙ строкой
// приложения «Напоминание: <задача> (<предмет>)» (NotificationHelper.kt). Вход slide-up.
export const S07: React.FC<SceneProps> = ({have, wide}) => {
	const {fps} = useVideoConfig();
	return (
		<SceneWrap bg={C.bg} in="slide-up">
			<Stage
				wide={wide}
				screen={
					<>
						<Sequence durationInFrames={50} premountFor={fps}>
							<KenBurns scale={[1.08, 1.18]} y={[40, -60]}>
								<Shot name="a7_task_dialog" have={have} />
							</KenBurns>
						</Sequence>
						{/* match-cut диалог → пуш */}
						<Sequence from={50} premountFor={fps}>
							<SceneWrap in="fade">
								<HomeScreen>
									<Shade>
										<NotificationCard
											title="Напоминание: Сдать лабораторную работу"
											body="(Физика)"
											when="вчера"
										/>
									</Shade>
								</HomeScreen>
							</SceneWrap>
						</Sequence>
					</>
				}
				title="Домашка не потеряется — приложение напомнит"
				accent="приложение напомнит"
				overlay={
					<Sequence durationInFrames={50}>
						<Callout delay={16} x={124 + 416} y={84 + 1050} lx={640} ly={38} label="напоминание" />
					</Sequence>
				}
			/>
		</SceneWrap>
	);
};
