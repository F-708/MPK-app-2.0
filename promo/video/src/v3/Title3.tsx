// Title3 — Golos 900 96px (≤16 симв/строку) иначе 84px, lh 1.06, tracking −1%, #0B3564,
// left 84 top 140, ≤2 строк; суб Golos 500 40px #5A6B80; вход fade+rise4px 8f без kinetic-scale.
import React from 'react';
import {clamp01} from './anim3';
import {C3, Golos3, TITLE_IN} from './theme3';

export const Title3: React.FC<{lines: string[]; sub: string; frame: number}> = ({lines, sub, frame}) => {
	const fs = Math.max(...lines.map((l) => l.length)) <= 16 ? 96 : 84;
	const t = clamp01((frame - TITLE_IN) / 8);
	return (
		<div style={{position: 'absolute', left: 84, top: 140, opacity: t, transform: `translateY(${4 * (1 - t)}px)`}}>
			{lines.map((l, i) => (
				<div
					key={i}
					style={{
						fontFamily: Golos3,
						fontWeight: 900,
						fontSize: fs,
						lineHeight: 1.06,
						letterSpacing: '-1%',
						color: C3.navy,
					}}
				>
					{l}
				</div>
			))}
			<div style={{fontFamily: Golos3, fontWeight: 500, fontSize: 40, color: C3.sub, marginTop: 16}}>{sub}</div>
		</div>
	);
};
