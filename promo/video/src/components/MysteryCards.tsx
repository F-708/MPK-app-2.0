import React from 'react';
import {interpolate, useCurrentFrame} from 'remotion';
import {C, Golos} from '../theme';

// A9 «Загадка»: три карточки-силуэта под чёрными плашками «???»
// (темы / расписание другой группы / ещё кое-что); средняя приоткрывается
// на 0.4с (12 кадров) и снова гаснет. Янтарный «?» пульсирует на открытой.
const CARD_W = 260;
const CARD_H = 340;

export const MysteryCards: React.FC = () => {
	const frame = useCurrentFrame();
	const drift = -frame * 0.35; // лёгкий drift вверх
	// 20→26 открытие, 32→38 закрытие (0.4с ≈ 12 кадров приоткрыто)
	const open = interpolate(frame, [20, 26, 32, 38], [0, 1, 1, 0], {
		extrapolateLeft: 'clamp',
		extrapolateRight: 'clamp',
	});
	const pulse = 0.6 + 0.4 * Math.sin((frame / 18) * 2 * Math.PI);

	return (
		<div
			style={{
				position: 'absolute',
				inset: 0,
				display: 'flex',
				alignItems: 'center',
				justifyContent: 'center',
				gap: 32,
				translate: `0 ${drift}px`,
			}}
		>
			{[0, 1, 2].map((i) => {
				const o = i === 1 ? open : 0;
				return (
					<div
						key={i}
						style={{
							position: 'relative',
							width: CARD_W,
							height: CARD_H,
							borderRadius: 24,
							backgroundColor: '#FFFFFF',
							overflow: 'hidden',
							opacity: interpolate(frame, [i * 3, 8 + i * 3], [0, 1], {
								extrapolateLeft: 'clamp',
								extrapolateRight: 'clamp',
							}),
							boxShadow: '0 12px 40px rgba(0,0,0,0.35)',
						}}
					>
						{/* силуэт: скелетон-строки */}
						<div style={{padding: 30, display: 'flex', flexDirection: 'column', gap: 22}}>
							<div style={{width: 90, height: 90, borderRadius: '50%', backgroundColor: '#D8DEE6'}} />
							{[190, 150, 170, 120].map((w, j) => (
								<div key={j} style={{width: w, height: 20, borderRadius: 10, backgroundColor: '#E4E9EF'}} />
							))}
							{o > 0 && (
								<div
									style={{
										position: 'absolute',
										inset: 0,
										display: 'flex',
										alignItems: 'center',
										justifyContent: 'center',
										fontFamily: Golos,
										fontWeight: 900,
										fontSize: 110,
										color: C.amber,
										opacity: pulse,
									}}
								>
									?
								</div>
							)}
						</div>
						{/* чёрная плашка «???» */}
						<div
							style={{
								position: 'absolute',
								inset: 0,
								backgroundColor: '#0A0F14',
								display: 'flex',
								alignItems: 'center',
								justifyContent: 'center',
								fontFamily: Golos,
								fontWeight: 900,
								fontSize: 60,
								letterSpacing: 4,
								color: C.amber,
								opacity: 1 - o,
								scale: `${1 + 0.06 * o}`,
							}}
						>
							???
						</div>
					</div>
				);
			})}
		</div>
	);
};
