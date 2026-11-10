// PromoF6 — 30s, 9:16, функция «Оформление». Изолированная линия, чужих файлов не трогает.
// Границы сцен: 0/120/480/630/810/900
//  S1 0-120   Hook: «Оформление» + ряд из 8 точек-пресетов
//  S2 120-480 Карусель 8 пресетов: экран перекрашивается crossfade'ами (по 45f)
//  S3 480-630 Светлая → тёмная тема (фирменный пресет)
//  S4 630-810 Погодный эффект: снег с дрейфом за контентом
//  S5 810-900 Финал: «Мой Политех»
// Цвета пресетов — честные, из app/.../ui/theme/AppTheme.kt (light brand/active).
import React from 'react';
import {AbsoluteFill, Sequence, interpolate, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, Golos} from '../theme';

const B = [0, 120, 480, 630, 810, 900];

// name, light brand (topBar), light active (акцент), dark brand, dark active
const PRESETS: {name: string; brand: string; active: string; dBrand: string; dActive: string}[] = [
	{name: 'Фирменная', brand: C.navy, active: C.blue, dBrand: '#7FA8D9', dActive: '#5B9BD5'},
	{name: 'Изумрудная', brand: '#0B5D3B', active: '#0A7447', dBrand: '#689B85', dActive: '#3EA177'},
	{name: 'Бордовая', brand: '#7B1225', active: '#A81B33', dBrand: '#B87F89', dActive: '#CB7685'},
	{name: 'Графитовая', brand: '#37474F', active: '#546E7A', dBrand: '#8FA0A8', dActive: '#80949D'},
	{name: 'Индиго', brand: '#3730A3', active: '#4F46E5', dBrand: '#8B87CA', dActive: '#8B85EE'},
	{name: 'Янтарная', brand: '#92400E', active: '#B45309', dBrand: '#B98565', dActive: '#C88049'},
	{name: 'Стальная', brand: '#075985', active: '#0369A1', dBrand: '#6095B1', dActive: '#4F96BD'},
	{name: 'Сливовая', brand: '#6D2A57', active: '#8E3573', dBrand: '#C993B8', dActive: '#E18ACB'},
];

const ease = (f: number, a: number, b: number) =>
	interpolate(f, [a, b], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});

const Title: React.FC<{children: React.ReactNode; size?: number; color?: string}> = ({children, size = 84, color = C.navy}) => (
	<div style={{fontFamily: Golos, fontWeight: 900, fontSize: size, color, lineHeight: 1.06, letterSpacing: -1}}>
		{children}
	</div>
);

const Sub: React.FC<{children: React.ReactNode; color?: string; size?: number}> = ({children, color = C.sub, size = 40}) => (
	<div style={{fontFamily: Golos, fontWeight: 500, fontSize: size, color, lineHeight: 1.3}}>
		{children}
	</div>
);

// Мини-экран приложения: шапка в brand, карточки расписания, активный таб в active.
const AppScreen: React.FC<{brand: string; active: string; dark?: boolean; w?: number}> = ({
	brand, active, dark = false, w = 720,
}) => {
	const bg = dark ? '#14181D' : '#FFFFFF';
	const ink = dark ? '#E8ECF1' : C.ink;
	const sub = dark ? '#8A94A1' : C.sub;
	const line = dark ? '#262C34' : '#E7ECF1';
	const b = dark ? (PRESETS.find(p => p.brand === brand)?.dBrand ?? brand) : brand;
	const a = dark ? (PRESETS.find(p => p.active === active)?.dActive ?? active) : active;
	return (
		<div style={{
			width: w, borderRadius: 44, overflow: 'hidden', background: bg,
			boxShadow: '0 24px 80px rgba(11,53,100,0.18)', border: `2px solid ${line}`,
		}}>
			<div style={{background: b, padding: '44px 40px 36px', color: '#FFFFFF'}}>
				<div style={{fontFamily: Golos, fontWeight: 900, fontSize: 46}}>Расписание</div>
				<div style={{fontFamily: Golos, fontWeight: 500, fontSize: 28, opacity: 0.85, marginTop: 6}}>Понедельник, 10 ноября</div>
			</div>
			<div style={{padding: '32px 36px 44px', display: 'flex', flexDirection: 'column', gap: 22}}>
				{[['09:00', 'Математический анализ', 'ауд. 204'], ['10:50', 'Физика', 'ауд. 315'], ['12:50', 'Информатика', 'лаб. 7']].map(([t, s, r], i) => (
					<div key={i} style={{display: 'flex', gap: 24, alignItems: 'center', background: dark ? '#1B2129' : '#F7F9FB', borderRadius: 24, padding: '24px 28px', border: `2px solid ${line}`}}>
						<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 32, color: a, width: 120}}>{t}</div>
						<div style={{flex: 1}}>
							<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 34, color: ink}}>{s}</div>
							<div style={{fontFamily: Golos, fontWeight: 500, fontSize: 26, color: sub, marginTop: 4}}>{r}</div>
						</div>
					</div>
				))}
				<div style={{display: 'flex', justifyContent: 'center', gap: 14, marginTop: 8}}>
					{[0, 1, 2, 3].map(i => (
						<div key={i} style={{width: i === 0 ? 44 : 16, height: 16, borderRadius: 8, background: i === 0 ? a : line}} />
					))}
				</div>
			</div>
		</div>
	);
};

const Dots: React.FC<{current: number; color?: string}> = ({current, color}) => (
	<div style={{display: 'flex', gap: 22}}>
		{PRESETS.map((p, i) => (
			<div key={i} style={{
				width: 34, height: 34, borderRadius: 17, background: p.active,
				opacity: i === current ? 1 : 0.35,
				transform: `scale(${i === current ? 1.25 : 1})`,
				border: i === current ? `4px solid ${color ?? C.ink}` : 'none',
			}} />
		))}
	</div>
);

// ---------- scenes ----------

const S1: React.FC = () => {
	const f = useCurrentFrame();
	const {fps} = useVideoConfig();
	const s = spring({frame: f, fps, config: {damping: 18, stiffness: 110}});
	return (
		<AbsoluteFill style={{background: C.bg, justifyContent: 'center', alignItems: 'center', gap: 56, opacity: ease(f, 0, 12) * (1 - ease(f, B[1] - 20, B[1] - 4))}}>
			<div style={{transform: `translateY(${(1 - s) * 40}px)`, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 44}}>
				<Title size={110}>Оформление</Title>
				<Sub size={44}>8 цветовых тем на любой вкус</Sub>
				<Dots current={Math.min(PRESETS.length - 1, Math.floor(f / 11))} color={C.bg} />
			</div>
		</AbsoluteFill>
	);
};

const S2: React.FC = () => {
	const f = useCurrentFrame(); // 0..359 внутри сцены
	const per = 45;
	const idx = Math.min(PRESETS.length - 1, Math.floor(f / per));
	const next = Math.min(PRESETS.length - 1, idx + 1);
	const t = ease(f, idx * per + per - 14, idx * per + per); // crossfade в конце каждого слота
	const p = PRESETS[idx];
	const n = PRESETS[next];
	const len = 360; // локальная длительность сцены
	return (
		<AbsoluteFill style={{background: C.bg, alignItems: 'center', justifyContent: 'center', gap: 48, opacity: 1 - ease(f, len - 20, len - 4)}}>
			<Sub size={42}>Один тап — и всё приложение перекрашено</Sub>
			<div style={{position: 'relative'}}>
				<AppScreen brand={p.brand} active={p.active} />
				<div style={{position: 'absolute', inset: 0, opacity: t}}>
					<AppScreen brand={n.brand} active={n.active} />
				</div>
			</div>
			<div style={{display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 26}}>
				<div style={{fontFamily: Golos, fontWeight: 900, fontSize: 62, color: C.ink}}>
					{(t > 0.5 ? n : p).name}
				</div>
				<Dots current={t > 0.5 ? next : idx} />
			</div>
		</AbsoluteFill>
	);
};

const S3: React.FC = () => {
	const f = useCurrentFrame();
	const t = ease(f, 40, 95); // светлая → тёмная
	const p = PRESETS[0];
	return (
		<AbsoluteFill style={{background: t > 0.5 ? '#0C1015' : C.bg, alignItems: 'center', justifyContent: 'center', gap: 52, opacity: 1 - ease(f, 130, 146)}}>
			<Title size={72} color={t > 0.5 ? '#E8ECF1' : C.navy}>День и ночь</Title>
			<div style={{position: 'relative'}}>
				<AppScreen brand={p.brand} active={p.active} />
				<div style={{position: 'absolute', inset: 0, opacity: t}}>
					<AppScreen brand={p.brand} active={p.active} dark />
				</div>
			</div>
			<Sub size={40} color={t > 0.5 ? '#8A94A1' : C.sub}>Тёмная тема — для каждого пресета</Sub>
		</AbsoluteFill>
	);
};

// Снег: детерминированные частицы, дрейф sin, за контентом (zIndex ниже карточки).
const Snow: React.FC<{count?: number; color?: string}> = ({count = 90, color = '#FFFFFF'}) => {
	const f = useCurrentFrame();
	return (
		<>
			{Array.from({length: count}, (_, i) => {
				const seed = (i * 137.508) % 100;
				const x0 = (i * 97.3) % 1080;
				const speed = 2.2 + (seed % 10) / 5;
				const size = 5 + (seed % 9);
				const y = ((f * speed + seed * 40) % 2100) - 100;
				const x = x0 + Math.sin((f + i * 20) / 28) * (14 + (seed % 18));
				return (
					<div key={i} style={{
						position: 'absolute', left: x, top: y, width: size, height: size,
						borderRadius: size / 2, background: color, opacity: 0.35 + (seed % 50) / 100,
					}} />
				);
			})}
		</>
	);
};

const S4: React.FC = () => {
	const f = useCurrentFrame();
	const p = PRESETS[4]; // Индиго — зимой красиво
	return (
		<AbsoluteFill style={{background: 'linear-gradient(180deg,#0E1330 0%,#1E2560 100%)', alignItems: 'center', justifyContent: 'center', gap: 52, opacity: ease(f, 0, 12) * (1 - ease(f, 160, 176))}}>
			<Snow />
			<div style={{zIndex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 48}}>
				<Title size={72} color="#FFFFFF">Погода за окном</Title>
				<AppScreen brand={p.brand} active={p.active} dark />
				<Sub size={40} color="#B9C0E0">Снег и дождь — за контентом, не мешая</Sub>
			</div>
		</AbsoluteFill>
	);
};

const S5: React.FC = () => {
	const f = useCurrentFrame();
	const {fps} = useVideoConfig();
	const s = spring({frame: f, fps, config: {damping: 16, stiffness: 100}});
	return (
		<AbsoluteFill style={{background: C.navy, justifyContent: 'center', alignItems: 'center', opacity: ease(f, 0, 14)}}>
			<div style={{transform: `scale(${0.92 + s * 0.08})`, opacity: s}}>
				<div style={{fontFamily: Golos, fontWeight: 900, fontSize: 120, color: '#FFFFFF', letterSpacing: -2}}>Мой Политех</div>
			</div>
		</AbsoluteFill>
	);
};

export const VideoF6: React.FC = () => (
	<AbsoluteFill style={{background: C.bg}}>
		<Sequence from={B[0]} durationInFrames={B[1] - B[0]}><S1 /></Sequence>
		<Sequence from={B[1]} durationInFrames={B[2] - B[1]}><S2 /></Sequence>
		<Sequence from={B[2]} durationInFrames={B[3] - B[2]}><S3 /></Sequence>
		<Sequence from={B[3]} durationInFrames={B[4] - B[3]}><S4 /></Sequence>
		<Sequence from={B[4]} durationInFrames={B[5] - B[4]}><S5 /></Sequence>
	</AbsoluteFill>
);
