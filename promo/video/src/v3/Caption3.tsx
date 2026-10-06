// Caption3 — mono 600 52px #0072CE tracking+4% FIXED top-right (x≤984, y=200 safe-area), один на сцену.
// Crossfade 4f: трек рендерится на уровне видео (окна in/out в абсолютных кадрах).
import React from 'react';
import {clamp01} from './anim3';
import {C3, Mono3} from './theme3';

export const Caption3: React.FC<{text: string; frame: number; inF: number; outF: number}> = ({
	text,
	frame,
	inF,
	outF,
}) => {
	const o = clamp01((frame - inF) / 4) * (1 - clamp01((frame - outF) / 4));
	if (o <= 0) return null;
	return (
		<div
			style={{
				position: 'absolute',
				top: 200,
				right: 96, // правый край текста x=984 (safe-area)
				fontFamily: Mono3,
				fontWeight: 600,
				fontSize: 52,
				letterSpacing: '4%',
				color: C3.blue,
				opacity: o,
			}}
		>
			{text}
		</div>
	);
};

export const CaptionTrack: React.FC<{frame: number; items: {text: string; inF: number; outF: number}[]}> = ({
	frame,
	items,
}) => (
	<>
		{items.map((it, i) => (
			<Caption3 key={i} text={it.text} frame={frame} inF={it.inF} outF={it.outF} />
		))}
	</>
);
