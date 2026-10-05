// SecondsChip3 — белая карточка radius24 тень-стек, mono 96 #0B3564, pop ζ0.6 на B+20,
// тик 4:32→4:29 каждый 30f. Стоит вне карточки шота (низ кадра, справа).
import React from 'react';
import {spr} from './anim3';
import {CHIP_SHADOW, C3, Mono3} from './theme3';

const VALUES = ['4:32', '4:31', '4:30', '4:29'];

export const SecondsChip3: React.FC<{frame: number; fps: number}> = ({frame, fps}) => {
	if (frame < 20) return null;
	const p = spr(frame, fps, 0.6, 12, 20);
	const idx = Math.min(3, Math.floor((frame - 20) / 30));
	return (
		<div
			style={{
				position: 'absolute',
				right: 84,
				bottom: 150,
				backgroundColor: C3.white,
				borderRadius: 24,
				boxShadow: CHIP_SHADOW,
				padding: '8px 44px',
				transform: `scale(${0.8 + 0.2 * p})`,
			}}
		>
			<span style={{fontFamily: Mono3, fontWeight: 700, fontSize: 96, color: C3.navy, fontVariantNumeric: 'tabular-nums'}}>
				{VALUES[idx]}
			</span>
		</div>
	);
};
