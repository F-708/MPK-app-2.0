import React from 'react';
import {Sequence, useVideoConfig} from 'remotion';
import {C, SceneProps} from '../theme';
import {SceneWrap, Stage} from '../components/Layout';
import {Shot} from '../components/Shot';
import {KenBurns} from '../components/KenBurns';
import {Callout} from '../components/Callout';
import {DEMO_ROWS, HomeScreen, WidgetToday} from '../components/AndroidChrome';

// A3 (180–300): уроки — слот «Сегодня»→«Завтра» (пан по слотам, янтарная выноска «Завтра»);
// match-cut на домашний экран с РЕПЛИКОЙ виджета расписания (widget_today_schedule.xml,
// WidgetStyle.LIGHT) — зум на виджет, затем янтарная рамка вокруг виджета.
export const S03: React.FC<SceneProps> = ({have, wide}) => {
	const {fps} = useVideoConfig();
	return (
		<SceneWrap bg={C.bg}>
			<Stage
				wide={wide}
				screen={
					<>
						<Sequence durationInFrames={60} premountFor={fps}>
							<KenBurns scale={[1.1, 1.14]} x={[60, -60]}>
								<Shot name="a3_lessons" have={have} />
							</KenBurns>
						</Sequence>
						{/* match-cut: домашний экран + виджет (реплика); виджет статичен, рамка дописывается */}
						<Sequence from={60} premountFor={fps}>
							<HomeScreen>
								<WidgetToday rows={DEMO_ROWS} />
							</HomeScreen>
						</Sequence>
					</>
				}
				title={"Уроки сегодня и завтра.\nИ прямо на домашнем экране"}
				accent="на домашнем экране"
				overlay={
					<>
						{/* выноска на слот «Завтра» (верхняя часть экрана уроков) */}
						<Sequence durationInFrames={60}>
							<Callout delay={14} x={124 + 620} y={84 + 320} lx={700} ly={232} label="Завтра" />
						</Sequence>
						{/* рамка вокруг виджета на домашнем экране (виджет поднят к статусбару, vision-QA f250) */}
						<Sequence from={60}>
							<Callout delay={20} x={130} y={206} rect={{w: 820, h: 360}} />
						</Sequence>
					</>
				}
			/>
		</SceneWrap>
	);
};
