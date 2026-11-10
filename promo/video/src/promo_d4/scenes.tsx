import React from 'react';
import {
	AbsoluteFill,
	interpolate,
	spring,
	useCurrentFrame,
	useVideoConfig,
} from 'remotion';
import {C, Golos, Mono} from '../theme';

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

// ─── Рамка домашнего экрана ──────────────────────────────────────────────────
// Геометрический мок: скруглённый прямоугольник, статус-бар, иконки-заглушки.
const PhoneHome: React.FC<{
	width: number;
	height: number;
	children?: React.ReactNode;
}> = ({width, height, children}) => {
	const pad = Math.round(width * 0.03);
	return (
		<div
			style={{
				width: width + pad * 2,
				height: height + pad * 2,
				borderRadius: Math.round(width * 0.1),
				background: '#0A0E14',
				padding: pad,
				boxShadow: '0 30px 80px rgba(11,53,100,0.45)',
			}}
		>
			<div
				style={{
					width,
					height,
					borderRadius: Math.round(width * 0.07),
					overflow: 'hidden',
					background: 'linear-gradient(160deg, #1B2A44 0%, #0B3564 70%, #0067BB 130%)',
					position: 'relative',
					padding: Math.round(width * 0.045),
				}}
			>
				{/* статус-бар */}
				<div
					style={{
						display: 'flex',
						justifyContent: 'space-between',
						alignItems: 'center',
						color: 'rgba(255,255,255,0.85)',
						fontFamily: Golos,
						fontWeight: 500,
						fontSize: 26,
						marginBottom: 28,
					}}
				>
					<span>9:41</span>
					<div style={{display: 'flex', gap: 10}}>
						<div
							style={{
								width: 34,
								height: 22,
								borderRadius: 4,
								border: '2px solid rgba(255,255,255,0.85)',
								position: 'relative',
							}}
						>
							<div
								style={{
									position: 'absolute',
									inset: 3,
									right: 8,
									background: 'rgba(255,255,255,0.85)',
									borderRadius: 2,
								}}
							/>
						</div>
					</div>
				</div>
				{children}
			</div>
		</div>
	);
};

// ─── Виджет «Сейчас и дальше» с прогресс-кольцом ─────────────────────────────
// Таймер до звонка: кольцо SVG заполняется от 100% к 0 по мере хода урока.
const NowWidget: React.FC<{
	lesson: string;
	teacher: string;
	room: string;
	totalSec: number; // полная длина паузы/урока в сек (для расчёта дуги)
	leftSec: number; // сколько осталось, сек
	highlight?: boolean;
	scale?: number;
}> = ({lesson, teacher, room, totalSec, leftSec, highlight = false, scale = 1}) => {
	const mm = String(Math.floor(leftSec / 60)).padStart(2, '0');
	const ss = String(Math.floor(leftSec % 60)).padStart(2, '0');
	const R = 92;
	const CIRC = 2 * Math.PI * R;
	const progress = 1 - leftSec / totalSec; // 0..1
	const dash = CIRC * progress;

	return (
		<div
			style={{
				transform: `scale(${scale})`,
				transformOrigin: 'top left',
				width: 720,
				background: '#FFFFFF',
				borderRadius: 36,
				padding: '34px 36px',
				boxShadow: highlight
					? '0 22px 60px rgba(0,103,187,0.45), 0 0 0 6px rgba(0,103,187,0.55)'
					: '0 14px 36px rgba(11,53,100,0.25)',
				display: 'flex',
				gap: 32,
				alignItems: 'center',
			}}
		>
			{/* Прогресс-кольцо */}
			<div style={{position: 'relative', width: 220, height: 220, flex: '0 0 auto'}}>
				<svg width={220} height={220} style={{transform: 'rotate(-90deg)'}}>
					<circle
						cx={110}
						cy={110}
						r={R}
						fill="none"
						stroke={C.widgetDivider}
						strokeWidth={18}
					/>
					<circle
						cx={110}
						cy={110}
						r={R}
						fill="none"
						stroke={C.blue}
						strokeWidth={18}
						strokeLinecap="round"
						strokeDasharray={`${dash} ${CIRC}`}
					/>
				</svg>
				<div
					style={{
						position: 'absolute',
						inset: 0,
						display: 'flex',
						flexDirection: 'column',
						alignItems: 'center',
						justifyContent: 'center',
					}}
				>
					<div
						style={{
							fontFamily: Mono,
							fontWeight: 700,
							fontSize: 52,
							color: C.ink,
							letterSpacing: -1,
						}}
					>
						{mm}:{ss}
					</div>
					<div
						style={{
							fontFamily: Golos,
							fontWeight: 500,
							fontSize: 22,
							color: C.sub,
							marginTop: 2,
						}}
					>
						до звонка
					</div>
				</div>
			</div>
			{/* Текст */}
			<div style={{flex: 1, minWidth: 0}}>
				<div
					style={{
						fontFamily: Golos,
						fontWeight: 700,
						fontSize: 24,
						color: C.blue,
						letterSpacing: 1.5,
						textTransform: 'uppercase',
						marginBottom: 8,
					}}
				>
					Сейчас
				</div>
				<div
					style={{
						fontFamily: Golos,
						fontWeight: 900,
						fontSize: 40,
						color: C.ink,
						lineHeight: 1.1,
						marginBottom: 6,
					}}
				>
					{lesson}
				</div>
				<div
					style={{
						fontFamily: Golos,
						fontWeight: 500,
						fontSize: 26,
						color: C.sub,
						marginBottom: 4,
					}}
				>
					{teacher}
				</div>
				<div
					style={{
						fontFamily: Golos,
						fontWeight: 700,
						fontSize: 26,
						color: C.navy,
					}}
				>
					{room}
				</div>
			</div>
		</div>
	);
};

// ─── Виджет «Расписание на сегодня» ─────────────────────────────────────────
const TodayWidget: React.FC<{
	rows: {time: string; name: string; room: string; active?: boolean}[];
	scale?: number;
}> = ({rows, scale = 1}) => {
	return (
		<div
			style={{
				transform: `scale(${scale})`,
				transformOrigin: 'top left',
				width: 720,
				background: '#FFFFFF',
				borderRadius: 36,
				padding: '30px 32px',
				boxShadow: '0 14px 36px rgba(11,53,100,0.25)',
			}}
		>
			<div
				style={{
					fontFamily: Golos,
					fontWeight: 700,
					fontSize: 26,
					color: C.navy,
					marginBottom: 18,
					letterSpacing: 0.5,
				}}
			>
				Сегодня
			</div>
			{rows.map((r, i) => (
				<div
					key={i}
					style={{
						display: 'flex',
						alignItems: 'center',
						gap: 22,
						padding: '14px 12px',
						marginBottom: i === rows.length - 1 ? 0 : 6,
						borderRadius: 18,
						background: r.active ? C.widgetHi : 'transparent',
					}}
				>
					<div
						style={{
							fontFamily: Mono,
							fontWeight: 700,
							fontSize: 26,
							color: r.active ? C.blue : C.sub,
							width: 130,
							flex: '0 0 auto',
						}}
					>
						{r.time}
					</div>
					<div
						style={{
							flex: 1,
							fontFamily: Golos,
							fontWeight: r.active ? 700 : 500,
							fontSize: 28,
							color: C.ink,
							whiteSpace: 'nowrap',
							overflow: 'hidden',
							textOverflow: 'ellipsis',
						}}
					>
						{r.name}
					</div>
					<div
						style={{
							fontFamily: Golos,
							fontWeight: 700,
							fontSize: 24,
							color: C.sub,
							flex: '0 0 auto',
						}}
					>
						{r.room}
					</div>
				</div>
			))}
		</div>
	);
};

// ─── Сцена 1. Хук ────────────────────────────────────────────────────────────
export const S1Hook: React.FC = () => {
	const t = useEnter(6);
	const s = useEnter(22);
	return (
		<AbsoluteFill style={{background: C.navy, justifyContent: 'center', padding: 90}}>
			<div style={t}>
				<Title size={92} color="#fff">
					Пара идёт.
					<br />
					Звонок скоро.
				</Title>
			</div>
			<div style={s}>
				<Sub color="#BFD3EA">Виджеты «Моего Политеха» — прямо на домашнем экране.</Sub>
			</div>
		</AbsoluteFill>
	);
};

// ─── Сцена 2. Домашний экран с двумя виджетами ───────────────────────────────
export const S2HomeScreen: React.FC = () => {
	const frame = useCurrentFrame();
	const phoneIn = spring({frame, fps: 30, config: {damping: 20, stiffness: 90}});
	const head = useEnter(2);
	const w1 = useEnter(24);
	const w2 = useEnter(48);

	// Тикающий таймер: 07:32 -> 07:04 на ~28 кадрах дальше (замедлим: -1с каждые 30 кадров)
	const startLeft = 7 * 60 + 32; // 452 сек
	const left = Math.max(0, startLeft - Math.floor(frame / 30));
	const total = 45 * 60; // 45 мин пара — дуга

	return (
		<AbsoluteFill style={{background: C.bg}}>
			<div style={{position: 'absolute', top: 100, left: 90, right: 90, ...head}}>
				<Title size={80}>Виджеты на домашнем экране</Title>
				<Sub>Что сейчас и что дальше — без запуска приложения.</Sub>
			</div>
			<div
				style={{
					position: 'absolute',
					top: 420,
					left: 0,
					right: 0,
					display: 'flex',
					justifyContent: 'center',
					opacity: phoneIn,
					transform: `translateY(${(1 - phoneIn) * 120}px)`,
				}}
			>
				<PhoneHome width={760} height={1280}>
					<div style={w1}>
						<NowWidget
							lesson="Математика"
							teacher="Иванова А. П."
							room="каб. 118"
							totalSec={total}
							leftSec={left}
							scale={0.92}
						/>
					</div>
					<div style={{height: 26}} />
					<div style={w2}>
						<TodayWidget
							scale={0.92}
							rows={[
								{time: '08:30', name: 'Русский язык', room: '204'},
								{time: '10:20', name: 'Математика', room: '118', active: true},
								{time: '12:10', name: 'Физика', room: '305'},
								{time: '13:50', name: 'История', room: '210'},
							]}
						/>
					</div>
				</PhoneHome>
			</div>
		</AbsoluteFill>
	);
};

// ─── Сцена 3. Таймер до звонка крупно ────────────────────────────────────────
export const S3Countdown: React.FC = () => {
	const frame = useCurrentFrame();
	const head = useEnter(4);
	const widgetIn = spring({frame: frame - 6, fps: 30, config: {damping: 16, stiffness: 90}});

	// Таймер тикает каждую секунду: 07:32 -> 00:12
	const startLeft = 7 * 60 + 32; // 452
	const left = Math.max(12, startLeft - Math.floor(frame / 30));
	const total = 45 * 60;

	// Пульс кольца
	const pulse = 1 + 0.02 * Math.sin(frame / 7);

	return (
		<AbsoluteFill style={{background: C.bg}}>
			<div style={{position: 'absolute', top: 100, left: 90, right: 90, ...head}}>
				<Title size={80}>До звонка — обратный отсчёт</Title>
				<Sub>Кольцо показывает, сколько осталось до конца пары.</Sub>
			</div>
			<div
				style={{
					position: 'absolute',
					top: 560,
					left: 0,
					right: 0,
					display: 'flex',
					justifyContent: 'center',
					opacity: widgetIn,
					transform: `translateY(${(1 - widgetIn) * 80}px) scale(${pulse})`,
				}}
			>
				<NowWidget
					lesson="Математика"
					teacher="Иванова А. П."
					room="каб. 118"
					totalSec={total}
					leftSec={left}
					highlight
					scale={1.18}
				/>
			</div>
			{/* Подпись с живым временем */}
			<div
				style={{
					position: 'absolute',
					top: 1420,
					left: 0,
					right: 0,
					display: 'flex',
					justifyContent: 'center',
					...useEnter(40),
				}}
			>
				<div
					style={{
						background: C.navy,
						color: '#fff',
						fontFamily: Mono,
						fontWeight: 700,
						fontSize: 60,
						padding: '24px 56px',
						borderRadius: 30,
						letterSpacing: 2,
						boxShadow: '0 20px 60px rgba(11,53,100,0.45)',
					}}
				>
					до звонка {String(Math.floor(left / 60)).padStart(2, '0')}:
					{String(left % 60).padStart(2, '0')}
				</div>
			</div>
		</AbsoluteFill>
	);
};

// ─── Сцена 4. Виджет «Расписание на сегодня» ─────────────────────────────────
export const S4TodayWidget: React.FC = () => {
	const frame = useCurrentFrame();
	const head = useEnter(4);
	const widgetIn = spring({frame: frame - 8, fps: 30, config: {damping: 18, stiffness: 100}});

	// Активная строка мягко подсвечивается
	const glow = 0.5 + 0.5 * Math.sin(frame / 10);

	return (
		<AbsoluteFill style={{background: C.bg}}>
			<div style={{position: 'absolute', top: 100, left: 90, right: 90, ...head}}>
				<Title size={80}>Весь день — в одном виджете</Title>
				<Sub>Время, предмет, кабинет. Текущая пара подсвечена.</Sub>
			</div>
			<div
				style={{
					position: 'absolute',
					top: 540,
					left: 0,
					right: 0,
					display: 'flex',
					justifyContent: 'center',
					opacity: widgetIn,
					transform: `translateY(${(1 - widgetIn) * 80}px)`,
				}}
			>
				<TodayWidget
					scale={1.18}
					rows={[
						{time: '08:30', name: 'Русский язык', room: '204'},
						{time: '10:20', name: 'Математика', room: '118', active: true},
						{time: '12:10', name: 'Физика', room: '305'},
						{time: '13:50', name: 'История', room: '210'},
						{time: '15:30', name: 'Физкультура', room: 'спортзал'},
					]}
				/>
			</div>
			<div
				style={{
					position: 'absolute',
					top: 1480,
					left: 0,
					right: 0,
					display: 'flex',
					justifyContent: 'center',
					...useEnter(60),
				}}
			>
				<div
					style={{
						background: `rgba(0,103,187,${0.85 + glow * 0.15})`,
						color: '#fff',
						fontFamily: Golos,
						fontWeight: 700,
						fontSize: 40,
						padding: '22px 46px',
						borderRadius: 26,
						boxShadow: '0 14px 40px rgba(0,103,187,0.45)',
					}}
				>
					Сейчас: Математика, каб. 118
				</div>
			</div>
		</AbsoluteFill>
	);
};

// ─── Сцена 5. Аутро ──────────────────────────────────────────────────────────
export const S5Outro: React.FC = () => {
	const frame = useCurrentFrame();
	const s = spring({frame, fps: 30, config: {damping: 16, stiffness: 80}});
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
					fontSize: 120,
					color: '#fff',
					letterSpacing: -2,
				}}
			>
				Мой Политех
			</div>
		</AbsoluteFill>
	);
};
