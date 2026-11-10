// PromoC3 — 30s, 9:16, функции «Задания» и «Оценки». Изолированная линия, чужих файлов не трогает.
// Границы сцен: 0/150/330/510/690/780/900
//  S1 0-150   Hook: «Задания и оценки — в одном приложении»
//  S2 150-330 Урок → добавление домашнего задания (поле ввода + чип даты)
//  S3 330-510 Список заданий → отметка «выполнено» (чек-бокс, галочка)
//  S4 510-690 Оценки: карточки предметов с оценками
//  S5 690-780 Прогресс: средний балл
//  S6 780-900 Финал: «Мой Политех»
import React from 'react';
import {AbsoluteFill, Sequence, interpolate, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, Golos} from '../theme';

const B = [0, 150, 330, 510, 690, 780, 900];
const BLUE = '#0067BB';

// ---------- primitives ----------

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

const Card: React.FC<{children: React.ReactNode; style?: React.CSSProperties}> = ({children, style}) => (
	<div style={{
		background: '#FFFFFF', borderRadius: 32, padding: '36px 40px',
		boxShadow: '0 12px 40px rgba(11,53,100,0.10)', ...style,
	}}>
		{children}
	</div>
);

const Title: React.FC<{children: React.ReactNode; size?: number; color?: string}> = ({children, size = 76, color = C.navy}) => (
	<div style={{fontFamily: Golos, fontWeight: 900, fontSize: size, color, lineHeight: 1.08, letterSpacing: -1}}>
		{children}
	</div>
);

const Sub: React.FC<{children: React.ReactNode; color?: string}> = ({children, color = C.sub}) => (
	<div style={{fontFamily: Golos, fontWeight: 500, fontSize: 38, color, lineHeight: 1.3}}>
		{children}
	</div>
);

const Check: React.FC<{size?: number; color?: string}> = ({size = 34, color = '#FFFFFF'}) => (
	<svg width={size} height={size} viewBox="0 0 24 24" fill="none">
		<path d="M4 12.5l5 5L20 6.5" stroke={color} strokeWidth={3.4} strokeLinecap="round" strokeLinejoin="round" />
	</svg>
);

// ---------- S1 hook ----------
const S1: React.FC = () => {
	const f = useCurrentFrame();
	return (
		<AbsoluteFill style={{background: C.bg, justifyContent: 'center', alignItems: 'center', padding: 90}}>
			<FadeSlide from={0} style={{textAlign: 'center'}}>
				<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 34, color: BLUE, letterSpacing: 6, textTransform: 'uppercase', marginBottom: 36}}>
					Мой Политех
				</div>
				<Title size={92}>
					Задания и оценки<br />— в одном месте
				</Title>
			</FadeSlide>
			<FadeSlide from={0} delay={18} style={{marginTop: 48, textAlign: 'center'}}>
				<Sub>Домашка к уроку. Выполнено — галочка.<br />Оценки — всегда под рукой.</Sub>
			</FadeSlide>
			{/* декоративные карточки-призраки */}
			<FadeSlide from={0} delay={30} dy={80} style={{position: 'absolute', bottom: 150, left: 90, right: 90, display: 'flex', gap: 28, opacity: 0.9}}>
				{['Задания', 'Оценки'].map((t, i) => (
					<Card key={t} style={{flex: 1, textAlign: 'center', padding: '28px 0'}}>
						<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 40, color: i === 0 ? BLUE : C.amber}}>{t}</div>
					</Card>
				))}
			</FadeSlide>
			<div style={{opacity: ease(f, 0, 12)}} />
		</AbsoluteFill>
	);
};

// ---------- S2 add homework ----------
const S2: React.FC = () => {
	const f = useCurrentFrame();
	const text = '§ 12, упр. 4–6';
	const typed = Math.floor(ease(f, 40, 70) * text.length);
	return (
		<AbsoluteFill style={{background: C.bg, padding: '140px 80px'}}>
			<FadeSlide from={0}><Title>Добавь задание<br />к уроку</Title></FadeSlide>

			{/* карточка урока */}
			<FadeSlide from={0} delay={12}>
				<Card style={{marginTop: 60}}>
					<div style={{fontFamily: Golos, fontWeight: 900, fontSize: 52, color: C.navy}}>Математика</div>
					<div style={{fontFamily: Golos, fontWeight: 500, fontSize: 34, color: C.sub, marginTop: 8}}>2 пара · 10:00–11:30 · каб. 214</div>
					<div style={{height: 2, background: '#E6ECF2', margin: '30px 0'}} />
					<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 34, color: BLUE, marginBottom: 16}}>Домашнее задание</div>
					{/* поле ввода */}
					<div style={{
						border: `3px solid ${BLUE}`, borderRadius: 20, padding: '26px 30px',
						fontFamily: Golos, fontWeight: 500, fontSize: 44, color: C.ink, minHeight: 96,
					}}>
						{text.slice(0, typed)}
						<span style={{opacity: Math.floor(f / 15) % 2 === 0 ? 1 : 0, color: BLUE}}>|</span>
					</div>
					{/* чип дедлайна */}
					<FadeSlide from={0} delay={55} style={{marginTop: 24, display: 'flex', gap: 20}}>
						<div style={{
							background: '#E8F1FA', borderRadius: 999, padding: '16px 34px',
							fontFamily: Golos, fontWeight: 700, fontSize: 34, color: BLUE,
						}}>
							Сдать: четверг
						</div>
					</FadeSlide>
				</Card>
			</FadeSlide>

			{/* кнопка сохранить */}
			<FadeSlide from={0} delay={95} style={{marginTop: 50}}>
				<div style={{
					background: BLUE, borderRadius: 28, padding: '30px 0', textAlign: 'center',
					fontFamily: Golos, fontWeight: 900, fontSize: 44, color: '#FFF',
					boxShadow: '0 14px 36px rgba(0,103,187,0.35)',
					transform: `scale(${1 + 0.04 * Math.sin(Math.min(1, Math.max(0, (f - 95) / 12)) * Math.PI)})`,
				}}>
					Сохранить
				</div>
			</FadeSlide>
		</AbsoluteFill>
	);
};

// ---------- S3 task list + done ----------
const S3: React.FC = () => {
	const f = useCurrentFrame();
	const tasks = [
		{t: 'Математика — § 12, упр. 4–6', doneAt: 78},
		{t: 'Физика — лаб. работа №3', doneAt: 108},
		{t: 'История — конспект гл. 5', doneAt: -1},
	];
	return (
		<AbsoluteFill style={{background: C.bg, padding: '140px 80px'}}>
			<FadeSlide from={0}><Title>Отмечай<br />выполненное</Title></FadeSlide>
			<div style={{marginTop: 60, display: 'flex', flexDirection: 'column', gap: 30}}>
				{tasks.map((task, i) => {
					const done = task.doneAt >= 0 && f >= task.doneAt;
					const appear = 12 + i * 10;
					return (
						<FadeSlide key={task.t} from={0} delay={appear}>
							<Card style={{display: 'flex', alignItems: 'center', gap: 30, padding: '32px 40px'}}>
								<div style={{
									width: 64, height: 64, borderRadius: 18, flexShrink: 0,
									border: `4px solid ${done ? BLUE : '#C6D2DE'}`,
									background: done ? BLUE : 'transparent',
									display: 'flex', alignItems: 'center', justifyContent: 'center',
									transition: 'none',
								}}>
									{done && <Check size={38} />}
								</div>
								<div style={{
									fontFamily: Golos, fontWeight: 500, fontSize: 40,
									color: done ? C.sub : C.ink,
									textDecoration: done ? 'line-through' : 'none',
								}}>
									{task.t}
								</div>
							</Card>
						</FadeSlide>
					);
				})}
			</div>
			<FadeSlide from={0} delay={120} style={{marginTop: 56, textAlign: 'center'}}>
				<Sub>Выполнено 2 из 3 — осталась история</Sub>
			</FadeSlide>
		</AbsoluteFill>
	);
};

// ---------- S4 grades ----------
const S4: React.FC = () => {
	const rows = [
		{s: 'Математика', g: [5, 4, 5]},
		{s: 'Физика', g: [4, 5]},
		{s: 'Программирование', g: [5, 5, 5]},
		{s: 'История', g: [4]},
	];
	const gc = (g: number) => (g === 5 ? BLUE : g === 4 ? '#2E9E5B' : C.amber);
	return (
		<AbsoluteFill style={{background: C.bg, padding: '140px 80px'}}>
			<FadeSlide from={0}><Title>Оценки —<br />всегда под рукой</Title></FadeSlide>
			<div style={{marginTop: 60, display: 'flex', flexDirection: 'column', gap: 26}}>
				{rows.map((r, i) => (
					<FadeSlide key={r.s} from={0} delay={10 + i * 9}>
						<Card style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '30px 40px'}}>
							<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 42, color: C.navy}}>{r.s}</div>
							<div style={{display: 'flex', gap: 14}}>
								{r.g.map((g, j) => (
									<div key={j} style={{
										width: 64, height: 64, borderRadius: 18, background: gc(g),
										display: 'flex', alignItems: 'center', justifyContent: 'center',
										fontFamily: Golos, fontWeight: 900, fontSize: 36, color: '#FFF',
									}}>
										{g}
									</div>
								))}
							</div>
						</Card>
					</FadeSlide>
				))}
			</div>
		</AbsoluteFill>
	);
};

// ---------- S5 average ----------
const S5: React.FC = () => {
	const f = useCurrentFrame();
	const p = ease(f, 20, 80);
	const avg = (4.7 * p).toFixed(1).replace('.', ',');
	const R = 210;
	const CIRC = 2 * Math.PI * R;
	return (
		<AbsoluteFill style={{background: C.bg, justifyContent: 'center', alignItems: 'center', padding: 90}}>
			<FadeSlide from={0} style={{textAlign: 'center'}}><Title>Весь прогресс<br />на одном экране</Title></FadeSlide>
			<FadeSlide from={0} delay={12} style={{marginTop: 80, position: 'relative', width: 500, height: 500}}>
				<svg width={500} height={500} viewBox="0 0 500 500">
					<circle cx={250} cy={250} r={R} fill="none" stroke="#E2E8F0" strokeWidth={36} />
					<circle
						cx={250} cy={250} r={R} fill="none" stroke={BLUE} strokeWidth={36}
						strokeLinecap="round"
						strokeDasharray={CIRC}
						strokeDashoffset={CIRC * (1 - 0.82 * p)}
						transform="rotate(-90 250 250)"
					/>
				</svg>
				<div style={{
					position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column',
					alignItems: 'center', justifyContent: 'center',
				}}>
					<div style={{fontFamily: Golos, fontWeight: 900, fontSize: 150, color: C.navy, lineHeight: 1}}>{avg}</div>
					<div style={{fontFamily: Golos, fontWeight: 500, fontSize: 36, color: C.sub, marginTop: 10}}>средний балл</div>
				</div>
			</FadeSlide>
		</AbsoluteFill>
	);
};

// ---------- S6 final ----------
const S6: React.FC = () => {
	return (
		<AbsoluteFill style={{background: C.navy, justifyContent: 'center', alignItems: 'center'}}>
			<FadeSlide from={0} dy={30} style={{textAlign: 'center'}}>
				<div style={{
					width: 150, height: 150, borderRadius: 40, background: BLUE, margin: '0 auto 60px',
					display: 'flex', alignItems: 'center', justifyContent: 'center',
					boxShadow: '0 20px 60px rgba(0,103,187,0.45)',
				}}>
					<Check size={86} />
				</div>
				<div style={{fontFamily: Golos, fontWeight: 900, fontSize: 110, color: '#FFFFFF', letterSpacing: -2}}>
					Мой Политех
				</div>
			</FadeSlide>
		</AbsoluteFill>
	);
};

// ---------- root ----------
const SCENES = [S1, S2, S3, S4, S5, S6];

export const VideoC3: React.FC = () => {
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
