import React from 'react';
import {AbsoluteFill, Img, staticFile, useCurrentFrame, interpolate} from 'remotion';
import {clamp} from './anim';
import {C, Roboto} from './theme';

export const S6Outro: React.FC<{dur?: number}> = ({dur = 135}) => {
	const frame = useCurrentFrame();
	const push = interpolate(frame, [0, 60], [1.06, 1], clamp); // отдаление камеры
	const t = interpolate(frame, [8, 26], [0, 1], clamp);
	const rise = interpolate(frame, [8, 26], [20, 0], clamp);
	const black = interpolate(frame, [dur - 15, dur], [0, 1], clamp);
	return (
		<AbsoluteFill style={{background: 'radial-gradient(circle at 50% 40%, #0B3564 0%, #001737 75%)'}}>
			<div style={{position: 'absolute', inset: 0, scale: `${push}`}}>
				<div style={{position: 'absolute', top: 560, width: '100%', textAlign: 'center', opacity: t, transform: `translateY(${rise}px)`}}>
					<Img src={staticFile('icon.svg')} style={{width: 170, height: 170, filter: 'brightness(0) invert(1)'}} />
					<div style={{fontFamily: Roboto, fontSize: 76, fontWeight: 900, color: C.white, marginTop: 36}}>
						Мой Политех
					</div>
					<div style={{fontFamily: Roboto, fontSize: 38, fontWeight: 500, color: 'rgba(255,255,255,0.72)', marginTop: 18}}>
						Расписание и сервисы колледжа
					</div>
				</div>
			</div>
			<AbsoluteFill style={{backgroundColor: '#000000', opacity: black, pointerEvents: 'none'}} />
		</AbsoluteFill>
	);
};
