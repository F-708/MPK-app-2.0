import React from 'react';
import {AbsoluteFill, Easing, Series, interpolate, useCurrentFrame} from 'remotion';
import {C, Golos} from '../../theme';
import {IMG_W, OX, PhoneDark, SCREEN, ScreenImg, shotPt} from '../PhoneDark';

// S5 (234–306) Tri-cut: один Series с тремя Sequence по 24 кадра, hard-cut на битах.
// Чипы-подписи снизу по центру каждого tri-cut (тёмная пилюля — НЕ белая плашка).
const GRADE_ANCHOR = shotPt(744, 1081); // центр «4.2» в shot_grades (824×1830)
// якорь в системе координат img-бокса (img стоит в left=OX, top=0, 749.2×1664):
const ORIGIN = `${GRADE_ANCHOR.left - OX}px ${GRADE_ANCHOR.top}px`; // ≈ 676.5px 982.9px

const Chip: React.FC<{children: React.ReactNode}> = ({children}) => {
	const frame = useCurrentFrame();
	const p = interpolate(frame, [0, 4], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	return (
		<div style={{position: 'absolute', left: 0, right: 0, top: 1580, display: 'flex', justifyContent: 'center', opacity: p}}>
			<div
				style={{
					fontFamily: Golos,
					fontWeight: 500,
					fontSize: 40,
					color: '#DCE7F2',
					backgroundColor: 'rgba(5,7,12,0.78)',
					border: '1px solid rgba(255,255,255,0.14)',
					borderRadius: 999,
					padding: '16px 40px',
				}}
			>
				{children}
			</div>
		</div>
	);
};

// (a) Оценки: cover-зум к «4.2» scale 1.0→1.35 (transform-origin — пиксель-якорь).
const Grades: React.FC = () => {
	const frame = useCurrentFrame();
	const s = interpolate(frame, [0, 24], [1.0, 1.35], {extrapolateRight: 'clamp'});
	return (
		<>
			<PhoneDark scale={0.78} left={206} top={200}>
				<div style={{position: 'absolute', inset: 0, overflow: 'hidden'}}>
					{/* objectFit fill в бокс 749.2×1664 = ровно contain-пропорция шота 824×1830 */}
					<ScreenImg
						name="shot_grades"
						style={{
							left: OX,
							top: 0,
							width: IMG_W,
							height: SCREEN.h,
							objectFit: 'fill',
							transform: `scale(${s})`,
							transformOrigin: ORIGIN,
						}}
					/>
				</div>
			</PhoneDark>
			<Chip>средний балл сам</Chip>
		</>
	);
};

// (b) Задания: реплика карточки скользит влево на 60% за 10 кадров, открывая #DC2626 + Delete;
// затем undo-снекбар-реплика 48dp (96px в shot-px) #001737 с полосой-таймером scaleX 1→0.
const TrashIcon: React.FC = () => (
	<svg width={56} height={56} viewBox="0 0 24 24" fill="#FFFFFF">
		<path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z" />
	</svg>
);

const Tasks: React.FC = () => {
	const frame = useCurrentFrame();
	const slide = interpolate(frame, [2, 12], [0, -60], {
		extrapolateLeft: 'clamp',
		extrapolateRight: 'clamp',
		easing: Easing.out(Easing.cubic),
	});
	const snack = interpolate(frame, [12, 15], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	const bar = interpolate(frame, [14, 24], [1, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	const rect = {position: 'absolute', left: OX + 36, top: 620, width: IMG_W - 72, height: 200} as React.CSSProperties;
	return (
		<>
			<PhoneDark scale={0.78} left={206} top={200}>
				<ScreenImg name="shot_tasks" />
				{/* красный слой под карточкой */}
				<div style={{...rect, backgroundColor: '#DC2626', borderRadius: 24, display: 'flex', alignItems: 'center', justifyContent: 'flex-end', padding: '0 40px', boxSizing: 'border-box'}}>
					<TrashIcon />
				</div>
				{/* реплика карточки задания (светлая = UI приложения, не титровая плашка) */}
				<div style={{...rect, backgroundColor: '#FFFFFF', borderRadius: 24, translate: `${slide}%`, padding: 32, boxSizing: 'border-box', boxShadow: '0 8px 24px rgba(0,0,0,0.18)'}}>
					<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 42, color: '#111820'}}>Лабораторная №4</div>
					<div style={{fontFamily: Golos, fontWeight: 500, fontSize: 34, color: '#6B7280', marginTop: 10}}>Физика · до 12 октября</div>
				</div>
				{/* undo-снекбар 48dp #001737 + полоса-таймер */}
				{frame >= 12 && (
					<div
						style={{
							position: 'absolute',
							left: OX + 24,
							top: 1380,
							width: IMG_W - 48,
							height: 96,
							backgroundColor: '#001737',
							borderRadius: 16,
							opacity: snack,
							translate: `0 ${(1 - snack) * 24}px`,
							display: 'flex',
							alignItems: 'center',
							justifyContent: 'space-between',
							padding: '0 28px',
							boxSizing: 'border-box',
							overflow: 'hidden',
						}}
					>
						<span style={{fontFamily: Golos, fontWeight: 500, fontSize: 34, color: '#FFFFFF'}}>Задание удалено</span>
						<span style={{fontFamily: Golos, fontWeight: 700, fontSize: 34, color: C.amber, letterSpacing: 1}}>ОТМЕНИТЬ</span>
						<div style={{position: 'absolute', left: 0, bottom: 0, width: '100%', height: 6, backgroundColor: C.amber, transform: `scaleX(${bar})`, transformOrigin: 'left center'}} />
					</div>
				)}
			</PhoneDark>
			<Chip>свайп — и отменил</Chip>
		</>
	);
};

// (c) Материалы: янтарный штрих (video-language акцент поверх текста) draw-on 12 кадров.
const Materials: React.FC = () => {
	const frame = useCurrentFrame();
	const draw = interpolate(frame, [2, 14], [1, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	return (
		<>
			<PhoneDark scale={0.78} left={206} top={200}>
				<ScreenImg name="shot_viewer_text" />
				<svg
					width={IMG_W}
					height={220}
					viewBox={`0 0 ${IMG_W} 220`}
					style={{position: 'absolute', left: OX, top: 700, overflow: 'visible'}}
				>
					<path
						d={`M 40 150 C 170 95, 300 175, 420 120 S 620 70, ${IMG_W - 40} 130`}
						fill="none"
						stroke={C.amber}
						strokeWidth={12}
						strokeLinecap="round"
						pathLength={1}
						strokeDasharray={1}
						strokeDashoffset={draw}
					/>
				</svg>
			</PhoneDark>
			<Chip>конспекты внутри</Chip>
		</>
	);
};

export const S15_5: React.FC = () => (
	<AbsoluteFill>
		<Series>
			<Series.Sequence durationInFrames={24}>
				<Grades />
			</Series.Sequence>
			<Series.Sequence durationInFrames={24}>
				<Tasks />
			</Series.Sequence>
			<Series.Sequence durationInFrames={24}>
				<Materials />
			</Series.Sequence>
		</Series>
	</AbsoluteFill>
);
