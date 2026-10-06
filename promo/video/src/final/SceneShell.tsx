import React from 'react';
import {AbsoluteFill, useCurrentFrame} from 'remotion';
import {fade} from './anim';
import {SceneOverlay} from './Overlay';
import {Phone} from './Phone';
import {ShotScreen} from './ShotScreen';
import {BG_LIGHT, PHONE} from './theme';

export const SceneShell: React.FC<{
	dur: number;
	label: string;
	line: string;
	shot: string;
	scale?: number[];
	fx?: number[];
	fy?: number[];
	overlays?: React.ReactNode;
}> = ({dur, label, line, shot, scale, fx, fy, overlays}) => {
	const frame = useCurrentFrame();
	return (
		<AbsoluteFill style={{background: BG_LIGHT, opacity: fade(frame, dur)}}>
			<SceneOverlay label={label} line={line} frame={frame} />
			<div style={{position: 'absolute', left: PHONE.x, top: PHONE.y}}>
				<Phone width={PHONE.w}>
					<ShotScreen shot={shot} dur={dur} scale={scale} fx={fx} fy={fy}>
						{overlays}
					</ShotScreen>
				</Phone>
			</div>
		</AbsoluteFill>
	);
};
