// PromoH8 — 30s, 9:16, общий обзор «всё в одном». Изолированная линия, чужих файлов не трогает.
// Границы сцен: 0/75/225/375/525/675/810/900
//  S0 0-75    Hook: «Всё в одном»
//  S1 75-225  Расписание
//  S2 225-375 Карта с маршрутом
//  S3 375-525 Задания
//  S4 525-675 Преподаватели
//  S5 675-810 Виджеты
//  S6 810-900 Финал: «Мой Политех»
import React from 'react';
import {AbsoluteFill, Sequence, interpolate, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, Golos, Mono} from '../theme';

const B = [0, 75, 225, 375, 525, 675, 810, 900];
const BLUE = C.blue;

const ease = (f: number, a: number, b: number) =>
	interpolate(f, [a, b], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});

const FadeSlide: React.FC<{from: number; delay?: number; dy?: number; children: React.ReactNode; style?: React.CSSProperties}> = ({
	from, delay = 0, dy = 40, children, style,
}) => {
	const f = useCurrentFrame() - from;
	const {fps} = useVideoConfig();
	const s = spring({frame: Math.max(0, f - delay), fps, config: {damping: 18, stiffness: 120}});
	return (
		<div style={{opacity: ease(f, delay, delay + 10), transform: `translateY(${(1 - s) * dy}px)`, ...style}}>
			{children}
		</div>
	);
};

const Title: React.FC<{children: React.ReactNode; size?: number; color?: string}> = ({children, size = 76, color = C.navy}) => (
	<div style={{fontFamily: Golos, fontWeight: 900, fontSize: size, color, lineHeight: 1.08, letterSpacing: -1}}>
		{children}
	</div>
);

const Sub: React.FC<{children: React.ReactNode; color?: string; size?: number}> = ({children, color = C.sub, size = 38}) => (
	<div style={{fontFamily: Golos, fontWeight: 500, fontSize: size, color, lineHeight: 1.3}}>
		{children}
	</div>
);

// Общий каркас фича-сцены: глиф в круге + подпись + детальная карточка
const FeatureScene: React.FC<{
	label: string;
	sub: string;
	glyph: React.ReactNode;
	accent?: string;
	detail: React.ReactNode;
}> = ({label, sub, glyph, accent = BLUE, detail}) => (
	<AbsoluteFill style={{background: C.bg, padding: '150px 70px'}}>
		<FadeSlide from={0} style={{display: 'flex', justifyContent: 'center'}}>
			<div style={{
				width: 260, height: 260, borderRadius: 130, background: accent,
				display: 'flex', alignItems: 'center', justifyContent: 'center',
				boxShadow: `0 24px 70px ${accent}59`,
			}}>
				{glyph}
			</div>
		</FadeSlide>
		<FadeSlide from={0} delay={8} style={{marginTop: 60, textAlign: 'center'}}>
			<Title size={88}>{label}</Title>
		</FadeSlide>
		<FadeSlide from={0} delay={16} style={{marginTop: 22, textAlign: 'center'}}>
			<Sub>{sub}</Sub>
		</FadeSlide>
		<FadeSlide from={0} delay={28} style={{marginTop: 60}}>
			{detail}
		</FadeSlide>
	</AbsoluteFill>
);

const Card: React.FC<{children: React.ReactNode; style?: React.CSSProperties}> = ({children, style}) => (
	<div style={{
		background: '#FFFFFF', borderRadius: 32, padding: '32px 36px',
		boxShadow: '0 12px 40px rgba(11,53,100,0.10)', ...style,
	}}>
		{children}
	</div>
);

// ---------- glyphs (геометрия, stroke, белое на акценте) ----------

const GlyphCalendar = (
	<svg width={130} height={130} viewBox="0 0 24 24" fill="none">
		<rect x="3" y="5" width="18" height="16" rx="3" stroke="#FFF" strokeWidth={1.6} />
		<path d="M3 10h18M8 3v4M16 3v4" stroke="#FFF" strokeWidth={1.6} strokeLinecap="round" />
		<circle cx="8.5" cy="14.5" r="1.5" fill="#FFF" />
		<circle cx="15.5" cy="14.5" r="1.5" fill="#FFF" opacity={0.55} />
		<circle cx="8.5" cy="18" r="1.5" fill="#FFF" opacity={0.55} />
	</svg>
);

const GlyphMap = (
	<svg width={130} height={130} viewBox="0 0 24 24" fill="none">
		<path d="M12 21s-6-5.1-6-10a6 6 0 1 1 12 0c0 4.9-6 10-6 10z" stroke="#FFF" strokeWidth={1.6} />
		<circle cx="12" cy="11" r="2.3" fill="#FFF" />
	</svg>
);

const GlyphTask = (
	<svg width={130} height={130} viewBox="0 0 24 24" fill="none">
		<rect x="4" y="3" width="16" height="18" rx="3" stroke="#FFF" strokeWidth={1.6} />
		<path d="M8 12.5l2.8 2.8L16.5 9" stroke="#FFF" strokeWidth={1.9} strokeLinecap="round" strokeLinejoin="round" />
	</svg>
);

const GlyphTeacher = (
	<svg width={130} height={130} viewBox="0 0 24 24" fill="none">
		<circle cx="12" cy="8.5" r="3.5" stroke="#FFF" strokeWidth={1.6} />
		<path d="M5 20c0-3.6 3.1-5.5 7-5.5s7 1.9 7 5.5" stroke="#FFF" strokeWidth={1.6} strokeLinecap="round" />
	</svg>
);

const GlyphWidget = (
	<svg width={130} height={130} viewBox="0 0 24 24" fill="none">
		<rect x="3" y="3" width="8.5" height="8.5" rx="2.5" stroke="#FFF" strokeWidth={1.6} />
		<rect x="12.5" y="3" width="8.5" height="8.5" rx="2.5" fill="#FFF" opacity={0.9} />
		<rect x="3" y="12.5" width="8.5" height="8.5" rx="2.5" fill="#FFF" opacity={0.9} />
		<rect x="12.5" y="12.5" width="8.5" height="8.5" rx="2.5" stroke="#FFF" strokeWidth={1.6} />
	</svg>
);

// ---------- S0 hook ----------
const S0: React.FC = () => {
	const f = useCurrentFrame();
	const icons = [GlyphCalendar, GlyphMap, GlyphTask, GlyphTeacher, GlyphWidget];
	return (
		<AbsoluteFill style={{background: C.bg, justifyContent: 'center', alignItems: 'center', padding: 90}}>
			<FadeSlide from={0} style={{textAlign: 'center'}}>
				<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 34, color: BLUE, letterSpacing: 6, textTransform: 'uppercase', marginBottom: 36}}>
					Мой Политех
				</div>
				<Title size={96}>Всё для учёбы<br />— в одном месте</Title>
			</FadeSlide>
			<div style={{position: 'absolute', bottom: 210, display: 'flex', gap: 34}}>
				{icons.map((g, i) => {
					const s = spring({frame: Math.max(0, f - 10 - i * 5), fps: 30, config: {damping: 14, stiffness: 140}});
					return (
						<div key={i} style={{
							width: 120, height: 120, borderRadius: 32, background: BLUE,
							display: 'flex', alignItems: 'center', justifyContent: 'center',
							opacity: ease(f, 10 + i * 5, 18 + i * 5),
							transform: `scale(${s})`,
							boxShadow: '0 10px 30px rgba(0,114,206,0.35)',
						}}>
							<div style={{transform: 'scale(0.55)'}}>{g}</div>
						</div>
					);
				})}
			</div>
		</AbsoluteFill>
	);
};

// ---------- S1 Расписание ----------
const S1: React.FC = () => {
	const rows = [
		{t: '08:30', s: 'Математика', r: '204'},
		{t: '10:10', s: 'Физика', r: '311'},
		{t: '12:00', s: 'Программирование', r: '418'},
	];
	return (
		<FeatureScene label="Расписание" sub="Пары, кабинеты и звонки — на день и на неделю" glyph={GlyphCalendar} detail={
			<Card style={{padding: '10px 36px'}}>
				{rows.map((r, i) => (
					<div key={i} style={{
						display: 'flex', alignItems: 'center', gap: 28, padding: '26px 0',
						borderTop: i ? `2px solid ${C.widgetDivider}` : 'none',
					}}>
						<div style={{fontFamily: Mono, fontWeight: 700, fontSize: 34, color: BLUE, width: 140}}>{r.t}</div>
						<div style={{flex: 1, fontFamily: Golos, fontWeight: 700, fontSize: 37, color: C.ink}}>{r.s}</div>
						<div style={{
							fontFamily: Golos, fontWeight: 700, fontSize: 30, color: BLUE,
							background: C.widgetHi, borderRadius: 14, padding: '8px 20px',
						}}>{r.r}</div>
					</div>
				))}
			</Card>
		} />
	);
};

// ---------- S2 Карта ----------
const S2: React.FC = () => {
	const f = useCurrentFrame();
	// маршрут рисуется: dashoffset от полной длины к 0
	const draw = ease(f, 40, 95);
	const LEN = 900;
	return (
		<FeatureScene label="Карта" sub="Маршрут до нужного кабинета — по этажам" glyph={GlyphMap} accent={C.navy} detail={
			<Card style={{padding: '36px', position: 'relative'}}>
				<svg width={760} height={420} viewBox="0 0 760 420" fill="none">
					{/* стены этажа */}
					<rect x={20} y={20} width={720} height={380} rx={24} stroke="#D9E2EC" strokeWidth={6} />
					<path d="M260 20v160M500 180v220M260 180h240" stroke="#E7EDF3" strokeWidth={6} />
					{/* маршрут */}
					<path
						d="M90 340 H430 V110 H660"
						stroke={BLUE} strokeWidth={10} strokeLinecap="round" strokeLinejoin="round"
						strokeDasharray={LEN} strokeDashoffset={LEN * (1 - draw)}
					/>
					<circle cx={90} cy={340} r={16} fill={C.amber} stroke="#FFF" strokeWidth={5} />
					<circle cx={660} cy={110} r={18} fill={BLUE} stroke="#FFF" strokeWidth={5} />
				</svg>
				<div style={{
					position: 'absolute', top: 60, right: 60,
					fontFamily: Golos, fontWeight: 700, fontSize: 30, color: '#FFF',
					background: BLUE, borderRadius: 14, padding: '10px 22px',
					opacity: draw > 0.9 ? 1 : 0,
				}}>
					Каб. 418
				</div>
			</Card>
		} />
	);
};

// ---------- S3 Задания ----------
const S3: React.FC = () => {
	const f = useCurrentFrame();
	const items = [
		{t: 'Лабораторная №4', d: 'до четверга', done: true},
		{t: 'Курсовая: чертёж', d: 'до пятницы', done: false},
		{t: 'Подготовиться к зачёту', d: '12 ноября', done: false},
	];
	const check = ease(f, 55, 80);
	return (
		<FeatureScene label="Задания" sub="Дедлайны и оценки — ничего не потеряется" glyph={GlyphTask} accent="#2E9E5B" detail={
			<Card style={{padding: '10px 36px'}}>
				{items.map((it, i) => (
					<div key={i} style={{
						display: 'flex', alignItems: 'center', gap: 26, padding: '26px 0',
						borderTop: i ? `2px solid ${C.widgetDivider}` : 'none',
					}}>
						<div style={{
							width: 56, height: 56, borderRadius: 16, flexShrink: 0,
							border: `4px solid ${it.done ? '#2E9E5B' : '#C6D2DE'}`,
							background: it.done ? '#2E9E5B' : 'transparent',
							display: 'flex', alignItems: 'center', justifyContent: 'center',
						}}>
							{it.done && (
								<svg width={32} height={32} viewBox="0 0 24 24" fill="none">
									<path
										d="M5 12.5l4.5 4.5L19 7.5" stroke="#FFF" strokeWidth={3} strokeLinecap="round" strokeLinejoin="round"
										strokeDasharray={24} strokeDashoffset={24 * (1 - check)}
									/>
								</svg>
							)}
						</div>
						<div style={{flex: 1, minWidth: 0}}>
							<div style={{
								fontFamily: Golos, fontWeight: 700, fontSize: 36, color: C.ink,
								textDecoration: it.done ? 'line-through' : 'none', opacity: it.done ? 0.55 : 1,
							}}>
								{it.t}
							</div>
							<div style={{fontFamily: Golos, fontWeight: 500, fontSize: 28, color: C.sub, marginTop: 4}}>{it.d}</div>
						</div>
					</div>
				))}
			</Card>
		} />
	);
};

// ---------- S4 Преподаватели ----------
const S4: React.FC = () => {
	const people = [
		{n: 'Ирина Петрова', s: 'Математика', tone: BLUE},
		{n: 'Сергей Козлов', s: 'Физика', tone: '#7A5AF8'},
		{n: 'Анна Миронова', s: 'Информатика', tone: C.amber},
	];
	return (
		<FeatureScene label="Преподаватели" sub="Фото, предметы и контакты — вся кафедра" glyph={GlyphTeacher} accent="#7A5AF8" detail={
			<Card style={{padding: '10px 36px'}}>
				{people.map((p, i) => (
					<div key={i} style={{
						display: 'flex', alignItems: 'center', gap: 26, padding: '24px 0',
						borderTop: i ? `2px solid ${C.widgetDivider}` : 'none',
					}}>
						<div style={{
							width: 92, height: 92, borderRadius: 46, flexShrink: 0,
							background: `linear-gradient(135deg, ${p.tone} 0%, ${C.navy} 100%)`,
							display: 'flex', alignItems: 'center', justifyContent: 'center',
							fontFamily: Golos, fontWeight: 900, fontSize: 34, color: '#FFF',
						}}>
							{p.n.split(' ').map((w) => w[0]).join('')}
						</div>
						<div style={{flex: 1, minWidth: 0}}>
							<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 36, color: C.ink}}>{p.n}</div>
							<div style={{fontFamily: Golos, fontWeight: 500, fontSize: 28, color: C.sub, marginTop: 4}}>{p.s}</div>
						</div>
					</div>
				))}
			</Card>
		} />
	);
};

// ---------- S5 Виджеты ----------
const S5: React.FC = () => (
	<FeatureScene label="Виджеты" sub="Следующая пара — прямо на рабочем столе" glyph={GlyphWidget} accent={C.navy} detail={
		<div style={{display: 'flex', flexDirection: 'column', gap: 26}}>
			{/* виджет: следующая пара */}
			<div style={{
				background: C.widgetMain, borderRadius: 32, padding: '30px 36px',
				boxShadow: '0 12px 40px rgba(11,53,100,0.18)',
				display: 'flex', alignItems: 'center', gap: 26,
			}}>
				<div style={{flex: 1}}>
					<div style={{fontFamily: Golos, fontWeight: 500, fontSize: 26, color: C.widgetSub}}>Следующая пара</div>
					<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 40, color: '#FFF', marginTop: 6}}>Физика · 311</div>
					<div style={{fontFamily: Golos, fontWeight: 500, fontSize: 28, color: C.widgetSub, marginTop: 4}}>через 20 минут</div>
				</div>
				<div style={{
					width: 92, height: 92, borderRadius: 24, background: BLUE,
					display: 'flex', alignItems: 'center', justifyContent: 'center',
				}}>
					<svg width={52} height={52} viewBox="0 0 24 24" fill="none">
						<circle cx={12} cy={12} r={8.5} stroke="#FFF" strokeWidth={1.8} />
						<path d="M12 7.5V12l3 2.5" stroke="#FFF" strokeWidth={1.8} strokeLinecap="round" />
					</svg>
				</div>
			</div>
			{/* виджет: расписание дня, компакт */}
			<div style={{
				background: '#FFF', borderRadius: 32, padding: '26px 36px',
				boxShadow: '0 12px 40px rgba(11,53,100,0.10)',
				display: 'flex', alignItems: 'center', justifyContent: 'space-between',
			}}>
				<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 34, color: C.widgetMain}}>Сегодня · 4 пары</div>
				<div style={{fontFamily: Mono, fontWeight: 700, fontSize: 30, color: BLUE}}>08:30–15:10</div>
			</div>
		</div>
	} />
);

// ---------- S6 final ----------
const S6: React.FC = () => (
	<AbsoluteFill style={{background: C.navy, justifyContent: 'center', alignItems: 'center'}}>
		<FadeSlide from={0} dy={30} style={{textAlign: 'center'}}>
			<div style={{
				width: 150, height: 150, borderRadius: 75, background: BLUE, margin: '0 auto 60px',
				display: 'flex', alignItems: 'center', justifyContent: 'center',
				fontFamily: Golos, fontWeight: 900, fontSize: 56, color: '#FFF',
				boxShadow: '0 20px 60px rgba(0,114,206,0.45)',
			}}>
				МП
			</div>
			<div style={{fontFamily: Golos, fontWeight: 900, fontSize: 110, color: '#FFFFFF', letterSpacing: -2}}>
				Мой Политех
			</div>
		</FadeSlide>
	</AbsoluteFill>
);

// ---------- root ----------
const SCENES = [S0, S1, S2, S3, S4, S5, S6];

export const VideoH8: React.FC = () => {
	const f = useCurrentFrame();
	const fadeOut = interpolate(f, [886, 898], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	return (
		<AbsoluteFill style={{background: C.bg}}>
			{SCENES.map((S, i) => (
				<Sequence key={i} from={B[i]} durationInFrames={B[i + 1] - B[i]}>
					<S />
				</Sequence>
			))}
			<AbsoluteFill style={{background: '#000', opacity: fadeOut, pointerEvents: 'none'}} />
		</AbsoluteFill>
	);
};
