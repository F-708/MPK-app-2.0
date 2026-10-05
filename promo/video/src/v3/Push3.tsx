// Push3 — cross-push 10f (окно B−5…B+5), направление одно на весь фильм.
// Вход (local 0…6): из +8%x, scale 1.03→1, blur 10→0 за 6f + spring ζ0.9 15f.
// Выход (local dur−5…dur): −6%x/−4%y, scale .94, blur 6, opacity→0 на последних 4f.
import React from 'react';
import {useCurrentFrame, useVideoConfig} from 'remotion';
import {easeInOut, spr} from './anim3';

export const Push3: React.FC<{
	dur: number;
	enter: 'open' | 'push';
	out?: boolean;
	children: React.ReactNode;
}> = ({dur, enter, out = true, children}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const sIn = enter === 'push' ? spr(frame, fps, 0.9, 15) : 1;
	const e6 = easeInOut(frame / 6);
	const tOut = out && frame > dur - 5 ? (frame - (dur - 5)) / 5 : 0;
	const opacity = out && frame > dur - 4 ? Math.min(1, (dur - frame) / 4) : 1;
	return (
		<div
			style={{
				position: 'absolute',
				inset: 0,
				opacity,
				transform:
					enter === 'push'
						? `translateX(${8 * (1 - e6)}%) scale(${1.03 - 0.03 * sIn})`
						: tOut
							? `translate(${-6 * tOut}%, ${-4 * tOut}%) scale(${1 - 0.06 * tOut})`
							: undefined,
				filter: [
					enter === 'push' && frame < 6 ? `blur(${10 * (1 - e6)}px)` : '',
					tOut ? `blur(${6 * tOut}px)` : '',
				]
					.filter(Boolean)
					.join(' '),
			}}
		>
			{children}
		</div>
	);
};
