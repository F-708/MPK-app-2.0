import React from 'react';
import {
	AbsoluteFill,
	interpolate,
	spring,
	useCurrentFrame,
	useVideoConfig,
} from 'remotion';
import {C, Golos} from './theme';
import {PhoneFrame} from './PhoneFrame';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

// Плавное появление: fade + slide-up на spring.
const useEnter = (delay: number) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const s = spring({frame: frame - delay, fps, config: {damping: 18, stiffness: 120}});
	return {opacity: s, transform: `translateY(${(1 - s) * 40}px)`};
};

const Title: React.FC<{children: React.ReactNode; size?: number; color?: string}> = ({
	children,
	size = 76,
	color = C.ink,
}) => (
	<div
		style={{
			fontFamily: Golos,
			fontWeight: 900,
			fontSize: size,
			lineHeight: 1.1,
			color,
			letterSpacing: -1,
		}}
	>
		{children}
	</div>
);

const Sub: React.FC<{children: React.ReactNode; color?: string}> = ({children, color = C.sub}) => (
	<div style={{fontFamily: Golos, fontWeight: 500, fontSize: 40, color, marginTop: 24}}>
		{children}
	</div>
);

// Выноска-чип поверх мокапа (кабинет / предмет / время).
const Chip: React.FC<{text: string; delay: number; top: number; left?: number; right?: number}> = ({
	text,
	delay,
	top,
	left,
	right,
}) => {
	const style = useEnter(delay);
	return (
		<div
			style={{
				position: 'absolute',
				top,
				left,
				right,
				...style,
				background: C.blue,
				color: '#fff',
				fontFamily: Golos,
				fontWeight: 700,
				fontSize: 34,
				padding: '18px 30px',
				borderRadius: 22,
				boxShadow: '0 14px 40px rgba(0,103,187,0.4)',
			}}
		>
			{text}
		</div>
	);
};

// ─── Сцена 1. Хук ────────────────────────────────────────────────────────────
export const S1Hook: React.FC = () => {
	const t = useEnter(6);
	const s = useEnter(20);
	return (
		<AbsoluteFill style={{background: C.navy, justifyContent: 'center', padding: 90}}>
			<div style={t}>
				<Title size={88} color="#fff">
					Утро. Пары,
					<br />
					кабинеты, звонки.
				</Title>
			</div>
			<div style={s}>
				<Sub color="#BFD3EA">Всё расписание колледжа — в одном приложении.</Sub>
			</div>
		</AbsoluteFill>
	);
};

// ─── Сцена 2. Расписание на сегодня ──────────────────────────────────────────
export const S2Today: React.FC = () => {
	const frame = useCurrentFrame();
	const phoneIn = spring({frame, fps: 30, config: {damping: 20, stiffness: 90}});
	const head = useEnter(4);
	// Медленный пан вниз по списку уроков.
	const pan = interpolate(frame, [40, 260], [0, 420], clamp);

	return (
		<AbsoluteFill style={{background: C.bg}}>
			<div style={{position: 'absolute', top: 110, left: 90, right: 90, ...head}}>
				<Title>Расписание на сегодня</Title>
				<Sub>Предмет, преподаватель, кабинет — сразу видно.</Sub>
			</div>
			<div
				style={{
					position: 'absolute',
					top: 430,
					left: 0,
					right: 0,
					display: 'flex',
					justifyContent: 'center',
					opacity: phoneIn,
					transform: `translateY(${(1 - phoneIn) * 120}px)`,
				}}
			>
				<PhoneFrame shot="shot_schedule_today.png" width={720} viewHeight={1300} shiftY={pan} />
			</div>
			<Chip text="каб. 118" delay={90} top={1560} right={140} />
			<Chip text="10:20 – 11:05" delay={120} top={1560} left={140} />
		</AbsoluteFill>
	);
};

// ─── Сцена 3. До звонка ──────────────────────────────────────────────────────
export const S3Countdown: React.FC = () => {
	const frame = useCurrentFrame();
	const head = useEnter(4);
	const badge = useEnter(30);
	// Лёгкий «пульс» бейджа.
	const pulse = 1 + 0.03 * Math.sin(frame / 6);
	const pan = interpolate(frame, [0, 150], [320, 480], clamp);

	return (
		<AbsoluteFill style={{background: C.bg}}>
			<div style={{position: 'absolute', top: 110, left: 90, right: 90, ...head}}>
				<Title>Сколько до звонка</Title>
				<Sub>Текущая пара подсвечена — видно, сколько осталось.</Sub>
			</div>
			<div
				style={{position: 'absolute', top: 450, left: 0, right: 0, display: 'flex', justifyContent: 'center'}}
			>
				<PhoneFrame shot="shot_schedule_today.png" width={700} viewHeight={1150} shiftY={pan} />
			</div>
			<div
				style={{
					position: 'absolute',
					top: 1690,
					left: 0,
					right: 0,
					display: 'flex',
					justifyContent: 'center',
					...badge,
				}}
			>
				<div
					style={{
						background: C.navy,
						color: '#fff',
						fontFamily: Golos,
						fontWeight: 900,
						fontSize: 52,
						padding: '26px 54px',
						borderRadius: 32,
						transform: `scale(${pulse})`,
						boxShadow: '0 20px 60px rgba(11,53,100,0.5)',
					}}
				>
					До звонка — 12 мин
				</div>
			</div>
		</AbsoluteFill>
	);
};

// ─── Сцена 4. Звонки ─────────────────────────────────────────────────────────
export const S4Bells: React.FC = () => {
	const frame = useCurrentFrame();
	const phoneIn = spring({frame, fps: 30, config: {damping: 20, stiffness: 90}});
	const head = useEnter(4);
	const pan = interpolate(frame, [30, 160], [0, 300], clamp);

	return (
		<AbsoluteFill style={{background: C.bg}}>
			<div style={{position: 'absolute', top: 110, left: 90, right: 90, ...head}}>
				<Title>И расписание звонков</Title>
				<Sub>Какая пара идёт сейчас — видно с первого взгляда.</Sub>
			</div>
			<div
				style={{
					position: 'absolute',
					top: 430,
					left: 0,
					right: 0,
					display: 'flex',
					justifyContent: 'center',
					opacity: phoneIn,
					transform: `translateY(${(1 - phoneIn) * 120}px)`,
				}}
			>
				<PhoneFrame shot="shot_bells.png" width={720} viewHeight={1300} shiftY={pan} />
			</div>
			<Chip text="Сейчас: 3 урок" delay={60} top={1560} left={140} />
		</AbsoluteFill>
	);
};

// ─── Сцена 5. Аутро ──────────────────────────────────────────────────────────
export const S5Outro: React.FC = () => {
	const frame = useCurrentFrame();
	const s = spring({frame, fps: 30, config: {damping: 16, stiffness: 80}});
	const sub = useEnter(18);
	return (
		<AbsoluteFill
			style={{
				background: C.navy,
				justifyContent: 'center',
				alignItems: 'center',
				flexDirection: 'column',
			}}
		>
			<div
				style={{
					opacity: s,
					transform: `translateY(${(1 - s) * 30}px)`,
					fontFamily: Golos,
					fontWeight: 900,
					fontSize: 110,
					color: '#fff',
					letterSpacing: -2,
				}}
			>
				Мой Политех
			</div>
			<div style={{...sub, fontFamily: Golos, fontWeight: 500, fontSize: 40, color: '#BFD3EA', marginTop: 20}}>
				Расписание МГПК
			</div>
		</AbsoluteFill>
	);
};
