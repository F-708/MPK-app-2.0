// PromoE5 — 30s, 9:16, функция «Преподаватели». Изолированная линия, чужих файлов не трогает.
// Границы сцен: 0/150/360/540/750/900
//  S1 0-150   Hook: «Преподаватели — в одном приложении»
//  S2 150-360 Список преподавателей (аватары-круги, инициалы)
//  S3 360-540 Поиск по фамилии (тайпинг, фильтрация списка)
//  S4 540-750 Карточка преподавателя: фото + заглушка-силуэт без фото
//  S5 750-900 Финал: «Мой Политех»
import React from 'react';
import {AbsoluteFill, Sequence, interpolate, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, Golos} from '../theme';

const B = [0, 150, 360, 540, 750, 900];
const BLUE = C.blue;

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
		background: '#FFFFFF', borderRadius: 32, padding: '32px 36px',
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

const Sub: React.FC<{children: React.ReactNode; color?: string; size?: number}> = ({children, color = C.sub, size = 38}) => (
	<div style={{fontFamily: Golos, fontWeight: 500, fontSize: size, color, lineHeight: 1.3}}>
		{children}
	</div>
);

// Аватар-круг: либо «фото» (декоративный градиент с инициалами поверх), либо силуэт-заглушка.
const Avatar: React.FC<{initials: string; size?: number; tone?: string; photo?: boolean}> = ({
	initials, size = 120, tone = BLUE, photo = true,
}) => (
	<div style={{
		width: size, height: size, borderRadius: size / 2, flexShrink: 0,
		background: photo
			? `linear-gradient(135deg, ${tone} 0%, ${C.navy} 100%)`
			: '#DCE4EC',
		display: 'flex', alignItems: 'center', justifyContent: 'center',
		fontFamily: Golos, fontWeight: 900, fontSize: size * 0.36,
		color: photo ? '#FFFFFF' : '#8CA0B4',
		position: 'relative', overflow: 'hidden',
	}}>
		{photo ? initials : (
			// силуэт: голова + плечи
			<svg width={size * 0.62} height={size * 0.62} viewBox="0 0 24 24" fill="none">
				<circle cx="12" cy="8.4" r="3.6" fill="#8CA0B4" />
				<path d="M4.4 20c0-4 3.4-6.4 7.6-6.4s7.6 2.4 7.6 6.4" fill="#8CA0B4" />
			</svg>
		)}
	</div>
);

// Строка списка: аватар + ФИО + предмет
const Row: React.FC<{name: string; sub: string; initials: string; tone: string; photo?: boolean}> = ({
	name, sub, initials, tone, photo = true,
}) => (
	<Card style={{display: 'flex', alignItems: 'center', gap: 28, padding: '26px 32px'}}>
		<Avatar initials={initials} size={110} tone={tone} photo={photo} />
		<div style={{flex: 1, minWidth: 0}}>
			<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 40, color: C.ink, lineHeight: 1.15}}>{name}</div>
			<div style={{fontFamily: Golos, fontWeight: 500, fontSize: 31, color: C.sub, marginTop: 6}}>{sub}</div>
		</div>
		<svg width={36} height={36} viewBox="0 0 24 24" fill="none">
			<path d="M9 5l7 7-7 7" stroke="#B9C7D4" strokeWidth={2.8} strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	</Card>
);

// ---------- S1 hook ----------
const S1: React.FC = () => {
	return (
		<AbsoluteFill style={{background: C.bg, justifyContent: 'center', alignItems: 'center', padding: 90}}>
			<FadeSlide from={0} style={{textAlign: 'center'}}>
				<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 34, color: BLUE, letterSpacing: 6, textTransform: 'uppercase', marginBottom: 36}}>
					Мой Политех
				</div>
				<Title size={92}>
					Все преподаватели<br />— в телефоне
				</Title>
			</FadeSlide>
			<FadeSlide from={0} delay={18} style={{marginTop: 48, textAlign: 'center'}}>
				<Sub>Фото, предметы, кабинет.<br />Найти — по фамилии за секунду.</Sub>
			</FadeSlide>
			<FadeSlide from={0} delay={30} dy={80} style={{position: 'absolute', bottom: 160, display: 'flex', gap: 36}}>
				<Avatar initials="ИП" size={140} tone={BLUE} />
				<Avatar initials="СК" size={140} tone={C.amber} />
				<Avatar initials="" size={140} photo={false} />
			</FadeSlide>
		</AbsoluteFill>
	);
};

// ---------- S2 teacher list ----------
const S2: React.FC = () => {
	const rows = [
		{name: 'Иванова Мария Петровна', sub: 'Математика · каб. 214', initials: 'ИМ', tone: BLUE, photo: true},
		{name: 'Смирнов Алексей Игоревич', sub: 'Физика · каб. 301', initials: 'СА', tone: '#2E9E5B', photo: true},
		{name: 'Кузнецова Ольга Владимировна', sub: 'История · каб. 118', initials: 'КО', tone: C.amber, photo: true},
		{name: 'Петров Дмитрий Сергеевич', sub: 'Физкультура', initials: '', tone: '', photo: false},
	];
	return (
		<AbsoluteFill style={{background: C.bg, padding: '130px 70px'}}>
			<FadeSlide from={0}><Title>Преподаватели</Title></FadeSlide>
			<FadeSlide from={0} delay={6} style={{marginTop: 14}}>
				<Sub size={32}>Колледж МГПК · 4 из 48</Sub>
			</FadeSlide>
			<div style={{marginTop: 50, display: 'flex', flexDirection: 'column', gap: 26}}>
				{rows.map((r, i) => (
					<FadeSlide key={r.name} from={0} delay={12 + i * 10}>
						<Row {...r} />
					</FadeSlide>
				))}
			</div>
		</AbsoluteFill>
	);
};

// ---------- S3 search ----------
const S3: React.FC = () => {
	const f = useCurrentFrame();
	const text = 'иван';
	const typed = Math.floor(ease(f, 30, 66) * text.length);
	const q = text.slice(0, typed);
	const filtered = q.length >= 3; // «ива» → отфильтровано
	const list = filtered
		? [{name: 'Иванова Мария Петровна', sub: 'Математика · каб. 214', initials: 'ИМ', tone: BLUE, photo: true}]
		: [
			{name: 'Иванова Мария Петровна', sub: 'Математика · каб. 214', initials: 'ИМ', tone: BLUE, photo: true},
			{name: 'Смирнов Алексей Игоревич', sub: 'Физика · каб. 301', initials: 'СА', tone: '#2E9E5B', photo: true},
			{name: 'Кузнецова Ольга Владимировна', sub: 'История · каб. 118', initials: 'КО', tone: C.amber, photo: true},
		];
	return (
		<AbsoluteFill style={{background: C.bg, padding: '130px 70px'}}>
			<FadeSlide from={0}><Title>Найди по фамилии</Title></FadeSlide>

			{/* поисковая строка */}
			<FadeSlide from={0} delay={10} style={{marginTop: 50}}>
				<div style={{
					background: '#FFF', borderRadius: 28, border: `3px solid ${BLUE}`,
					padding: '28px 34px', display: 'flex', alignItems: 'center', gap: 22,
					boxShadow: '0 12px 40px rgba(11,53,100,0.10)',
				}}>
					<svg width={44} height={44} viewBox="0 0 24 24" fill="none">
						<circle cx="11" cy="11" r="7" stroke={BLUE} strokeWidth={2.6} />
						<path d="M20 20l-3.4-3.4" stroke={BLUE} strokeWidth={2.6} strokeLinecap="round" />
					</svg>
					<div style={{fontFamily: Golos, fontWeight: 500, fontSize: 46, color: C.ink, minHeight: 60}}>
						{q}
						<span style={{opacity: Math.floor(f / 15) % 2 === 0 ? 1 : 0, color: BLUE}}>|</span>
					</div>
				</div>
			</FadeSlide>

			{/* результат */}
			<div style={{marginTop: 46, display: 'flex', flexDirection: 'column', gap: 26}}>
				{list.map((r, i) => (
					<FadeSlide key={r.name} from={0} delay={filtered ? 78 : 20 + i * 8}>
						<Row {...r} />
					</FadeSlide>
				))}
			</div>

			{filtered && (
				<FadeSlide from={0} delay={88} style={{marginTop: 40, textAlign: 'center'}}>
					<Sub>Найдена 1 из 48</Sub>
				</FadeSlide>
			)}
		</AbsoluteFill>
	);
};

// ---------- S4 teacher card ----------
const S4: React.FC = () => {
	const f = useCurrentFrame();
	return (
		<AbsoluteFill style={{background: C.bg, padding: '130px 70px'}}>
			<FadeSlide from={0}><Title>Карточка<br />преподавателя</Title></FadeSlide>

			{/* основная карточка с «фото» */}
			<FadeSlide from={0} delay={12}>
				<Card style={{marginTop: 50, padding: '44px 40px'}}>
					<div style={{display: 'flex', alignItems: 'center', gap: 32}}>
						<Avatar initials="ИМ" size={180} tone={BLUE} />
						<div style={{flex: 1}}>
							<div style={{fontFamily: Golos, fontWeight: 900, fontSize: 48, color: C.navy, lineHeight: 1.12}}>
								Иванова<br />Мария Петровна
							</div>
						</div>
					</div>
					<div style={{height: 2, background: '#E6ECF2', margin: '34px 0'}} />
					<FadeSlide from={0} delay={34}>
						<div style={{display: 'flex', flexWrap: 'wrap', gap: 16}}>
							{['Математика', 'Алгебра', 'Геометрия'].map((t) => (
								<div key={t} style={{
									background: '#E8F1FA', borderRadius: 999, padding: '14px 30px',
									fontFamily: Golos, fontWeight: 700, fontSize: 32, color: BLUE,
								}}>
									{t}
								</div>
							))}
						</div>
					</FadeSlide>
					<FadeSlide from={0} delay={48} style={{marginTop: 32, display: 'flex', alignItems: 'center', gap: 18}}>
						<svg width={40} height={40} viewBox="0 0 24 24" fill="none">
							<path d="M12 21s-7-5.6-7-11a7 7 0 1114 0c0 5.4-7 11-7 11z" stroke={C.sub} strokeWidth={2.2} />
							<circle cx="12" cy="10" r="2.6" fill={C.sub} />
						</svg>
						<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 38, color: C.ink}}>Кабинет 214</div>
					</FadeSlide>
				</Card>
			</FadeSlide>

			{/* заглушка без фото */}
			<FadeSlide from={0} delay={90}>
				<Card style={{marginTop: 40, padding: '30px 36px', display: 'flex', alignItems: 'center', gap: 28}}>
					<Avatar initials="" size={110} photo={false} />
					<div style={{flex: 1}}>
						<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 38, color: C.ink}}>Петров Дмитрий Сергеевич</div>
						<div style={{fontFamily: Golos, fontWeight: 500, fontSize: 30, color: C.sub, marginTop: 6}}>Фото скоро появится</div>
					</div>
				</Card>
			</FadeSlide>

			<FadeSlide from={0} delay={120} style={{marginTop: 36, textAlign: 'center'}}>
				<Sub>Если фото нет — аккуратная заглушка</Sub>
			</FadeSlide>
			<div style={{opacity: ease(f, 0, 1)}} />
		</AbsoluteFill>
	);
};

// ---------- S5 final ----------
const S5: React.FC = () => {
	return (
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
};

// ---------- root ----------
const SCENES = [S1, S2, S3, S4, S5];

export const VideoE5: React.FC = () => {
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
