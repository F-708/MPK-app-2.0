// Сцены PromoFinal9x16. Границы: 0/105/270/420/585/765/900. Камера — Cam, оверлеи — в px шота.
import React from 'react';
import {AbsoluteFill, interpolate, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {PhoneMockup} from '../components/PhoneMockup';
import {Seconds} from '../components/Seconds';
import {Cam, ShotDiv, ShotLayer, ShotSvg} from './Cam';
import {Emblem} from './Emblem';
import {Plate} from './Plate';
import {BG, CF, F, R2} from './theme';

// Телефон стоит в одной точке во всех сценах — склейки Sequence бесшовны.
const Phone: React.FC<{children: React.ReactNode; style?: React.CSSProperties}> = ({children, style}) => (
	<div style={{position: 'absolute', left: 110, top: 70, ...style}}>
		<PhoneMockup width={860} aspect={2}>
			{children}
		</PhoneMockup>
	</div>
);

// S1 Intro 0–105: эмблема+заголовок, телефон всплывает снизу по пружине.
export const F1: React.FC = () => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const rise =
		frame < 50
			? 0
			: spring({frame: frame - 50, fps, config: {damping: 14, mass: 0.8, stiffness: 100}, durationInFrames: 50});
	const emb = spring({frame, fps, config: {damping: 14, mass: 0.8, stiffness: 100}, durationInFrames: 40});
	const outT = interpolate(frame, [66, 92], [1, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	return (
		<AbsoluteFill style={{background: BG}}>
			<div
				style={{
					position: 'absolute',
					left: 0,
					right: 0,
					top: 540,
					display: 'flex',
					flexDirection: 'column',
					alignItems: 'center',
					gap: 34,
					opacity: outT,
					translate: `0 ${(1 - outT) * -60}px`,
				}}
			>
				<Emblem size={230} fill={CF.surface} style={{opacity: emb, scale: `${0.9 + 0.1 * emb}`}} />
				<div style={{fontFamily: F, fontWeight: 900, fontSize: 104, letterSpacing: '0.12em', color: '#FFFFFF'}}>
					МОЙ ПОЛИТЕХ
				</div>
				<div style={{fontFamily: F, fontWeight: 500, fontSize: 34, letterSpacing: '0.15em', color: '#8FB4DA'}}>
					МИНСКИЙ ПОЛИТЕХНИЧЕСКИЙ КОЛЛЕДЖ
				</div>
			</div>
			<Phone style={{translate: `0 ${(1 - rise) * 1900}px`}}>
				<Cam frame={frame} keys={[{f: 0, s: 1, x: 824, y: 1750}]}>
					<ShotLayer shot="shot_schedule_today" />
				</Cam>
			</Phone>
		</AbsoluteFill>
	);
};

// S2 Расписание 105–270: наезд на текущую пару + дорисовка синей окантовки карточки.
const CARD_PER = 2 * (1517 + 490);
export const F2: React.FC = () => {
	const frame = useCurrentFrame();
	const draw = interpolate(frame, [25, 60], [1, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	return (
		<AbsoluteFill style={{background: BG}}>
			<Phone>
				<Cam
					frame={frame}
					keys={[
						{f: 0, s: 1.0, x: 824, y: 1500},
						{f: 70, s: 1.06, x: 824, y: 2163},
						{f: 165, s: 1.08, x: 824, y: 2163},
					]}
				>
					<ShotLayer shot="shot_schedule_today">
						<ShotSvg>
							<rect
								x={66}
								y={1918}
								width={1517}
								height={490}
								rx={8}
								fill="none"
								stroke={CF.accent}
								strokeWidth={14}
								strokeDasharray={CARD_PER}
								strokeDashoffset={draw * CARD_PER}
							/>
						</ShotSvg>
					</ShotLayer>
				</Cam>
			</Phone>
			<Plate label="РАСПИСАНИЕ" text="Пары, аудитории и подгруппы офлайн" />
		</AbsoluteFill>
	);
};

// S3 Звонки 270–420: фокус на баннер «сейчас», пульс точки шкалы, тикающий отсчёт.
export const F3: React.FC = () => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const phase = ((frame / fps) % 1.8) / 1.8;
	const tri = phase < 0.5 ? phase * 2 : 2 - phase * 2;
	const pop = interpolate(frame, [20, 35], [0, 1], {
		extrapolateLeft: 'clamp',
		extrapolateRight: 'clamp',
	});
	return (
		<AbsoluteFill style={{background: BG}}>
			<Phone>
				<Cam
					frame={frame}
					keys={[
						{f: 0, s: 1.0, x: 824, y: 1500},
						{f: 45, s: 1.32, x: 1050, y: 1150},
						{f: 150, s: 1.38, x: 1050, y: 1080},
					]}
				>
					<ShotLayer shot="shot_bells">
						<ShotSvg>
							<circle cx={1601} cy={1691} r={26 + 16 * tri} fill="rgba(0,114,206,0.35)" />
							<circle cx={1601} cy={1691} r={20} fill={CF.accent} stroke="#FFFFFF" strokeWidth={6} />
						</ShotSvg>
						<ShotDiv x={1020} y={990} style={{opacity: pop, scale: `${0.9 + 0.1 * pop}`}}>
							<div
								style={{
									backgroundColor: CF.surface,
									border: `1px solid ${CF.border}`,
									borderRadius: R2,
									padding: '12px 24px',
									boxShadow: '0 8px 24px rgba(0,23,55,0.25)',
								}}
							>
								<div
									style={{
										fontFamily: F,
										fontWeight: 700,
										fontSize: 26,
										letterSpacing: '0.15em',
										color: CF.muted,
									}}
								>
									ДО ЗВОНКА
								</div>
								<Seconds
									from={252}
									style={{fontFamily: F, fontWeight: 700, fontSize: 62, color: CF.accent}}
								/>
							</div>
						</ShotDiv>
					</ShotLayer>
				</Cam>
			</Phone>
			<Plate label="ОТСЧЁТ ДО ЗВОНКА" text="Точное время до перемены на экране и в шторке" />
		</AbsoluteFill>
	);
};

// S4 Карта 420–585: наезд на план этажа, пунктирный маршрут (dashoffset) + маркер кабинета.
const ROUTE_LEN = 2950 - 1244;
export const F4: React.FC = () => {
	const frame = useCurrentFrame();
	const reveal = interpolate(frame, [25, 75], [0, 1], {
		extrapolateLeft: 'clamp',
		extrapolateRight: 'clamp',
		easing: (t) => t * t * (3 - 2 * t),
	});
	const pin = interpolate(frame, [100, 115], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	return (
		<AbsoluteFill style={{background: BG}}>
			<Phone>
				<Cam
					frame={frame}
					keys={[
						{f: 0, s: 1.0, x: 824, y: 1800},
						{f: 70, s: 1.5, x: 824, y: 2250},
						{f: 165, s: 1.55, x: 824, y: 2350},
					]}
				>
					<ShotLayer shot="shot_map_route">
						<ShotSvg>
							<path
								d="M825,1244 L827,2950"
								fill="none"
								stroke={CF.accent}
								strokeWidth={18}
								strokeLinecap="round"
								strokeDasharray="46 34"
								strokeDashoffset={(1 - reveal) * (ROUTE_LEN + 80)}
							/>
							{pin > 0 && (
								<circle
									cx={827}
									cy={2950}
									r={34 * pin}
									fill={CF.accent}
									stroke="#FFFFFF"
									strokeWidth={10 * pin}
								/>
							)}
						</ShotSvg>
						{pin > 0 && (
							<ShotDiv x={880} y={2860} style={{opacity: pin, scale: `${0.85 + 0.15 * pin}`}}>
								<div
									style={{
										backgroundColor: CF.surface,
										border: `1px solid ${CF.border}`,
										borderRadius: R2,
										padding: '10px 22px',
										fontFamily: F,
										fontWeight: 700,
										fontSize: 40,
										letterSpacing: '0.15em',
										color: CF.title,
										boxShadow: '0 8px 24px rgba(0,23,55,0.25)',
									}}
								>
									КАБ. 214
								</div>
							</ShotDiv>
						)}
					</ShotLayer>
				</Cam>
			</Phone>
			<Plate label="НАВИГАЦИЯ" text="Планы этажей и поиск нужной аудитории" />
		</AbsoluteFill>
	);
};

// S5 Учёба 585–765: кросс-фейд tasks→grades, акценты на бейджах и балле.
export const F5: React.FC = () => {
	const frame = useCurrentFrame();
	const fade = interpolate(frame, [85, 100], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	const dz = interpolate(frame, [20, 35], [1, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	const lab = interpolate(frame, [35, 50], [1, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	const score = interpolate(frame, [110, 130], [1, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	const PER_DZ = 2 * (100 + 62);
	const PER_LAB = 2 * (112 + 62);
	const PER_SC = 2 * (190 + 150);
	return (
		<AbsoluteFill style={{background: BG}}>
			<Phone>
				<div style={{position: 'absolute', inset: 0, opacity: 1 - fade}}>
					<Cam
						frame={frame}
						keys={[
							{f: 0, s: 1.0, x: 824, y: 950},
							{f: 85, s: 1.3, x: 1005, y: 1050},
						]}
					>
						<ShotLayer shot="shot_tasks">
							<ShotSvg>
								<rect
									x={1446}
									y={855}
									width={100}
									height={62}
									rx={4}
									fill="none"
									stroke={CF.accent}
									strokeWidth={10}
									strokeDasharray={PER_DZ}
									strokeDashoffset={dz * PER_DZ}
								/>
								<rect
									x={1438}
									y={1179}
									width={112}
									height={62}
									rx={4}
									fill="none"
									stroke={CF.badgeLab}
									strokeWidth={10}
									strokeDasharray={PER_LAB}
									strokeDashoffset={lab * PER_LAB}
								/>
							</ShotSvg>
						</ShotLayer>
					</Cam>
				</div>
				<div style={{position: 'absolute', inset: 0, opacity: fade}}>
					<Cam
						frame={frame}
						keys={[
							{f: 85, s: 1.22, x: 955, y: 2123},
							{f: 180, s: 1.32, x: 1015, y: 2123},
						]}
					>
						<ShotLayer shot="shot_grades">
							<ShotSvg>
								<rect
									x={1410}
									y={2050}
									width={190}
									height={150}
									rx={4}
									fill="none"
									stroke={CF.accent}
									strokeWidth={10}
									strokeDasharray={PER_SC}
									strokeDashoffset={score * PER_SC}
								/>
							</ShotSvg>
						</ShotLayer>
					</Cam>
				</div>
			</Phone>
			<Plate label="УЧЁБА В ОДНОМ МЕСТЕ" text="Задания, конспекты и средний балл" />
		</AbsoluteFill>
	);
};

// S6 Outro 765–900: отдаление камеры, финальная плашка; fade to black — в VideoFinal.
export const F6: React.FC = () => {
	const frame = useCurrentFrame();
	const shrink = interpolate(frame, [0, 60], [1, 0.86], {
		extrapolateLeft: 'clamp',
		extrapolateRight: 'clamp',
		easing: (t) => t * t * (3 - 2 * t),
	});
	const lift = interpolate(frame, [0, 60], [0, -40], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	const pop = interpolate(frame, [25, 55], [0, 1], {
		extrapolateLeft: 'clamp',
		extrapolateRight: 'clamp',
		easing: (t) => t * t * (3 - 2 * t),
	});
	return (
		<AbsoluteFill style={{background: BG}}>
			<Phone style={{scale: `${shrink}`, translate: `0 ${lift}px`}}>
				<Cam
					frame={frame}
					keys={[
						{f: 0, s: 1.3, x: 824, y: 2163},
						{f: 70, s: 1.0, x: 824, y: 1750},
					]}
				>
					<ShotLayer shot="shot_schedule_today" />
				</Cam>
			</Phone>
			<div
				style={{
					position: 'absolute',
					left: 0,
					right: 0,
					bottom: 90,
					display: 'flex',
					flexDirection: 'column',
					alignItems: 'center',
					gap: 22,
					opacity: pop,
					translate: `0 ${(1 - pop) * 40}px`,
				}}
			>
				<div
					style={{
						width: 168,
						height: 168,
						backgroundColor: CF.accent,
						borderRadius: R2,
						display: 'flex',
						alignItems: 'center',
						justifyContent: 'center',
						boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.35)',
					}}
				>
					<Emblem size={112} fill="#FFFFFF" />
				</div>
				<div style={{fontFamily: F, fontWeight: 700, fontSize: 84, color: '#FFFFFF'}}>Мой Политех</div>
				<div style={{fontFamily: F, fontWeight: 400, fontSize: 40, color: '#9FB3C8'}}>
					Расписание и сервисы колледжа
				</div>
			</div>
		</AbsoluteFill>
	);
};
