import React from 'react';
import {AbsoluteFill, Img, staticFile, useCurrentFrame} from 'remotion';
import {fade} from './anim';
import {Phone} from './Phone';
import {ShotScreen} from './ShotScreen';
import {C, PHONE, Roboto} from './theme';

// Эмблема ic_mpk_emblem тёмная (#0B1B3D) → белый силуэт через filter.
export const S1Intro: React.FC<{dur?: number}> = ({dur = 105}) => {
	const frame = useCurrentFrame();
	return (
		<AbsoluteFill
			style={{
				background: 'radial-gradient(circle at 50% 30%, #0B3564 0%, #001737 75%)',
				opacity: fade(frame, dur, 6, 10),
			}}
		>
			<div style={{position: 'absolute', top: 96, width: '100%', textAlign: 'center', opacity: fade(frame, dur, 8, 12)}}>
				<Img src={staticFile('icon.svg')} style={{width: 120, height: 120, filter: 'brightness(0) invert(1)'}} />
				<div
					style={{
						fontFamily: Roboto,
						fontSize: 56,
						fontWeight: 700,
						letterSpacing: '0.15em',
						color: C.white,
						marginTop: 24,
					}}
				>
					МОЙ ПОЛИТЕХ
				</div>
			</div>
			<div style={{position: 'absolute', left: PHONE.x, top: PHONE.y}}>
				<Phone width={PHONE.w} entrance>
					<ShotScreen shot="shot_schedule_today.png" dur={dur} scale={[1, 1]} />
				</Phone>
			</div>
		</AbsoluteFill>
	);
};
