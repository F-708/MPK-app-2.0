import React from 'react';
import {Sequence, useVideoConfig} from 'remotion';
import {C, SceneProps} from '../theme';
import {SceneWrap, Stage} from '../components/Layout';
import {Shot} from '../components/Shot';
import {KenBurns} from '../components/KenBurns';
import {Callout} from '../components/Callout';
import {BellDot} from '../components/BellDot';
import {CountdownWidget, DEMO_ROWS, HomeScreen, NotificationCard, Shade, WidgetToday} from '../components/AndroidChrome';
import {Seconds} from '../components/Seconds';

// A4 (300–420): звонки ×3 быстрой склейкой (~1.3с каждая, slide):
// а) таймлайн с родной бегущей точкой (оверлей BellDot, параметры приложения);
// б) строка-уведомление «до звонка 4:32» с тикающими секундами (реплика шторки);
// в) виджет «до звонка» (реплика widget_bell_countdown).
export const S04: React.FC<SceneProps> = ({have, wide}) => {
	const {fps} = useVideoConfig();
	return (
		<SceneWrap bg={C.bg}>
			<Stage
				wide={wide}
				screen={
					<>
						<Sequence durationInFrames={40} premountFor={fps}>
							<KenBurns scale={[1.04, 1.1]}>
								<Shot name="a4_bells_timeline" have={have} />
								{/* рельс точки — вдоль таймлайна звонков (координаты по кадру) */}
								<BellDot from={[150, 480]} to={[620, 1240]} />
							</KenBurns>
						</Sequence>
						<Sequence from={40} durationInFrames={40} premountFor={fps}>
							<SceneWrap in="slide-left">
								<HomeScreen>
									{/* home-реплика под scrim шторки, opacity 0.4 (vision-QA f350) */}
									<div style={{opacity: 0.4}}>
										<WidgetToday rows={DEMO_ROWS} />
									</div>
									<Shade>
										<NotificationCard
											title={
												<>
													до звонка <Seconds from={272} style={{fontSize: 40}} />
												</>
											}
											when="10:32"
										/>
									</Shade>
								</HomeScreen>
							</SceneWrap>
						</Sequence>
						<Sequence from={80} premountFor={fps}>
							<SceneWrap in="slide-left">
								<HomeScreen>
									<CountdownWidget from={272} lesson="2-й урок · Информатика" />
								</HomeScreen>
							</SceneWrap>
						</Sequence>
					</>
				}
				title="Когда звонок — видно вплоть до секунд"
				accent="вплоть до секунд"
				overlay={
					<Sequence from={40} durationInFrames={40}>
						<Callout delay={8} x={430} y={275} lx={660} ly={440} label="тикает по секундам" />
					</Sequence>
				}
			/>
		</SceneWrap>
	);
};
