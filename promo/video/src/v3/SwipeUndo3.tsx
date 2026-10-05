// SwipeUndo3 — свайп-реплика S5 поверх кропа shot_tasks: белая карточка slide −60% за 24f на
// B+35, под ней #DC2626+«Удалить»; undo-снекбар #001737 h96 radius16 вход 8f на B+65, hold 45f,
// полоса-таймер #0072CE scaleX1→0 за 45f, undo-тап (белый круг-курсор) на B+110, возврат 25f.
import React from 'react';
import {clamp01, easeInOut} from './anim3';
import {CHIP_SHADOW, C3, Golos3, Mono3} from './theme3';

export const SwipeRow3: React.FC<{frame: number}> = ({frame}) => {
	const out = easeInOut((frame - 35) / 24);
	const back = easeInOut((frame - 110) / 25);
	const x = -60 * out * (1 - back);
	return (
		<div style={{position: 'absolute', left: '6%', right: '6%', top: '58%', height: '17%'}}>
			<div
				style={{
					position: 'absolute',
					inset: 0,
					borderRadius: 24,
					backgroundColor: C3.red,
					display: 'flex',
					alignItems: 'center',
					justifyContent: 'flex-end',
					paddingRight: 40,
					opacity: clamp01(out * 2),
				}}
			>
				<span style={{fontFamily: Golos3, fontWeight: 700, fontSize: 40, color: C3.white}}>Удалить</span>
			</div>
			<div
				style={{
					position: 'absolute',
					inset: 0,
					borderRadius: 24,
					backgroundColor: C3.white,
					boxShadow: CHIP_SHADOW,
					transform: `translateX(${x}%)`,
					display: 'flex',
					alignItems: 'center',
					paddingLeft: 36,
					gap: 24,
				}}
			>
				<div style={{width: 64, height: 64, borderRadius: 16, backgroundColor: C3.radial}} />
				<div>
					<div style={{fontFamily: Golos3, fontWeight: 700, fontSize: 36, color: C3.navy}}>Лаба №4 — отчет</div>
					<div style={{fontFamily: Mono3, fontWeight: 600, fontSize: 26, color: C3.sub}}>сдать до 14:05</div>
				</div>
			</div>
		</div>
	);
};

export const SwipeUndo3: React.FC<{frame: number; top: number}> = ({frame, top}) => {
	const inT = clamp01((frame - 65) / 8);
	const gone = clamp01((frame - 118) / 8);
	const o = inT * (1 - gone);
	if (o <= 0) return null;
	const bar = 1 - clamp01((frame - 73) / 45);
	const tap = frame >= 110 ? 1 - 0.3 * Math.sin(clamp01((frame - 110) / 10) * Math.PI) : 0;
	return (
		<div
			style={{
				position: 'absolute',
				left: 140,
				right: 140,
				top,
				height: 96,
				borderRadius: 16,
				backgroundColor: C3.snack,
				opacity: o,
				transform: `translateY(${20 * (1 - inT)}px)`,
				boxShadow: CHIP_SHADOW,
				display: 'flex',
				alignItems: 'center',
				justifyContent: 'space-between',
				padding: '0 32px',
				overflow: 'hidden',
			}}
		>
			<span style={{fontFamily: Golos3, fontWeight: 500, fontSize: 36, color: C3.white}}>Задание сдано</span>
			<span style={{fontFamily: Golos3, fontWeight: 700, fontSize: 36, color: C3.blue, position: 'relative'}}>
				Отменить
				{tap ? (
					<span
						style={{
							position: 'absolute',
							left: '50%',
							top: '50%',
							width: 56,
							height: 56,
							margin: '-28px 0 0 -28px',
							borderRadius: '50%',
							backgroundColor: C3.white,
							opacity: 0.9,
							transform: `scale(${tap})`,
							boxShadow: CHIP_SHADOW,
						}}
					/>
				) : null}
			</span>
			<span
				style={{
					position: 'absolute',
					left: 0,
					bottom: 0,
					height: 6,
					width: '100%',
					backgroundColor: C3.blue,
					transform: `scaleX(${bar})`,
					transformOrigin: 'left',
				}}
			/>
		</div>
	);
};
