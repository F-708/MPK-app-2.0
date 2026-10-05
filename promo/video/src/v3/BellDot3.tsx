// BellDot3 — точка+ореол по пиксель-якорю рельса ПОЛНОГО шота shot_bells: x=795, y 511→610
// (824×1830). Пересчёт в crop S3 y[0.14,0.60]: yF = (y/1830 − 0.14)/0.46 → 0.3027→0.4203,
// xF = 795/824 = 0.9648 (crop по x полный). Позиция — в px карточки.
import React from 'react';
import {easeInOut} from './anim3';
import {C3} from './theme3';

const X_F = 795 / 824;
const Y0_F = (511 / 1830 - 0.14) / 0.46;
const Y1_F = (610 / 1830 - 0.14) / 0.46;

export const BellDot3: React.FC<{frame: number; dur: number; w: number; h: number}> = ({frame, dur, w, h}) => {
	const t = easeInOut(frame / Math.max(1, dur - 20));
	const x = X_F * w;
	const y = (Y0_F + (Y1_F - Y0_F) * t) * h;
	const pulse = (frame % 30) / 30; // микро-событие на холде: ореол дышит
	return (
		<svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} style={{position: 'absolute', inset: 0}}>
			<circle cx={x} cy={y} r={14 + 14 * pulse} fill="none" stroke={C3.blue} strokeWidth={3} opacity={0.5 * (1 - pulse)} />
			<circle cx={x} cy={y} r={11} fill={C3.blue} stroke={C3.white} strokeWidth={3} />
		</svg>
	);
};
