import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame} from 'remotion';
import {clamp, fade} from './anim';
import {FocusBox, SceneOverlay} from './Overlay';
import {Phone} from './Phone';
import {ShotScreen} from './ShotScreen';
import {BG_LIGHT, PHONE, SCR} from './theme';

// Первая половина сцены — задания (зум на бейджи ДЗ), со 90-го кадра экран оценок
// въезжает справа и зумит на средний балл (FocusBox).
export const S5Tasks: React.FC<{dur?: number}> = ({dur = 180}) => {
	const frame = useCurrentFrame();
	const half = Math.floor(dur / 2);
	const in2 = frame - half;
	const slide = interpolate(in2, [0, 16], [SCR.w, 0], clamp);
	const out1 = interpolate(in2, [0, 12], [1, 0], clamp);
	return (
		<AbsoluteFill style={{background: BG_LIGHT, opacity: fade(frame, dur)}}>
			<SceneOverlay label="УЧЁБА В ОДНОМ МЕСТЕ" line="Задания, конспекты и средний балл" frame={frame} />
			<div style={{position: 'absolute', left: PHONE.x, top: PHONE.y}}>
				<Phone width={PHONE.w}>
					<div style={{position: 'absolute', inset: 0}}>
						<div style={{position: 'absolute', inset: 0, opacity: out1}}>
							<ShotScreen shot="shot_tasks.png" dur={half} scale={[1, 1.26]} fx={[0.5, 0.8]} fy={[0.45, 0.3]} />
						</div>
						<div style={{position: 'absolute', inset: 0, translate: `${slide}px 0`}}>
							<ShotScreen shot="shot_grades.png" dur={dur - half} scale={[1, 1.3]} fx={[0.5, 0.85]} fy={[0.45, 0.58]}>
								<FocusBox fx={0.913} fy={0.58} w={0.14} h={0.07} frame={in2} at={25} />
							</ShotScreen>
						</div>
					</div>
				</Phone>
			</div>
		</AbsoluteFill>
	);
};
