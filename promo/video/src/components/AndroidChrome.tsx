import React from 'react';
import {Img, staticFile} from 'remotion';
import {C, Golos, Mono, SP} from '../theme';
import {Seconds} from './Seconds';

// Системный хром Android — РЕПЛИКИ (не app-UI): статусбар, домашний экран,
// виджеты по res/layout/widget_*.xml (цвета WidgetStyle.LIGHT), шторка уведомлений.
// Данные — вымышленная демо-группа «ИС-99» (п.7.3: без ПД).

export const StatusBar: React.FC = () => {
	return (
		<div
			style={{
				height: 72,
				padding: '0 40px',
				display: 'flex',
				alignItems: 'center',
				justifyContent: 'space-between',
				fontFamily: Golos,
				fontWeight: 500,
				fontSize: 32,
				color: C.widgetMain,
				flexShrink: 0,
			}}
		>
			<span style={{fontFamily: Mono, fontWeight: 700}}>10:32</span>
			<svg width={150} height={34} viewBox="0 0 150 34" fill={C.widgetMain}>
				<rect x={0} y={20} width={8} height={12} rx={2} />
				<rect x={12} y={13} width={8} height={19} rx={2} />
				<rect x={24} y={6} width={8} height={26} rx={2} />
				<path d="M56 8 a26 26 0 0 1 34 0 l-5 6 a18 18 0 0 0 -24 0 z" />
				<circle cx={73} cy={27} r={5} />
				<rect x={104} y={8} width={36} height={18} rx={5} fill="none" stroke={C.widgetMain} strokeWidth={3} />
				<rect x={108} y={12} width={22} height={10} rx={2} />
				<rect x={143} y={13} width={4} height={8} rx={2} />
			</svg>
		</div>
	);
};

// Телефон / сообщения / камера / галерея — простые глифы для дока (vision-QA f250).
const DOCK = [
	{bg: C.blue, glyph: <><rect x={18} y={6} width={20} height={44} rx={4} /><path d="M24 44h8" /></>},
	{bg: '#34A853', glyph: <><rect x={6} y={10} width={44} height={30} rx={8} /><path d="M16 40v10l10-10" /></>},
	{bg: C.amber, glyph: <><rect x={6} y={14} width={44} height={32} rx={6} /><circle cx={28} cy={30} r={9} /><path d="M20 14l3-6h10l3 6" /></>},
	{bg: '#8E9BAA', glyph: <><rect x={6} y={8} width={44} height={40} rx={6} /><circle cx={19} cy={20} r={4} /><path d="M10 42l12-14 8 9 6-7 10 12" /></>},
];

export const HomeScreen: React.FC<{children?: React.ReactNode}> = ({children}) => {
	return (
		<div
			style={{
				position: 'absolute',
				inset: 0,
				background: 'linear-gradient(165deg, #EEF2F6 0%, #D5DEE8 55%, #BCC9D8 100%)',
				display: 'flex',
				flexDirection: 'column',
			}}
		>
			<StatusBar />
			<div
				style={{
					flex: 1,
					display: 'flex',
					flexDirection: 'column',
					justifyContent: 'flex-start',
					gap: 30,
					padding: '60px 16px 0',
				}}
			>
				{children}
			</div>
			{/* док: цветные круги с белыми глифами (vision-QA f250) */}
			<div style={{display: 'flex', justifyContent: 'center', gap: 40, paddingBottom: 46}}>
				{DOCK.map((d, i) => (
					<div
						key={i}
						style={{
							width: 116,
							height: 116,
							borderRadius: '50%',
							backgroundColor: d.bg,
							opacity: 0.85,
							boxShadow: '0 6px 18px rgba(11,53,100,0.18)',
							display: 'flex',
							alignItems: 'center',
							justifyContent: 'center',
						}}
					>
						<svg width={56} height={56} viewBox="0 0 56 56" fill="none" stroke="#FFFFFF" strokeWidth={4} strokeLinecap="round" strokeLinejoin="round">
							{d.glyph}
						</svg>
					</div>
				))}
			</div>
		</div>
	);
};

// Виджет «Расписание на сегодня» — реплика widget_today_schedule.xml + widget_row_list.xml,
// WidgetStyle.LIGHT: фон белый, заголовок #0B3564, номер 13sp bold, время 12sp subColor,
// предмет 13sp mainColor, чип кабинета highlightColor+mainColor, текущий урок — подсветка.
export type WRow = {n: number; time: string; subject: string; room?: string; current?: boolean; past?: boolean};

export const WidgetToday: React.FC<{rows: WRow[]}> = ({rows}) => {
	const rowColor = (r: WRow) => (r.past ? C.widgetSub : C.widgetMain);
	return (
		<div
			style={{
				backgroundColor: '#FFFFFF',
				borderRadius: 36,
				padding: '24px 26px',
				boxShadow: '0 8px 28px rgba(11,53,100,0.14)',
				// ширина ~800px при экране 860 (vision-QA f250)
				width: 800,
				maxWidth: '100%',
				margin: '0 auto',
				boxSizing: 'border-box',
			}}
		>
			<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 10 * SP, letterSpacing: 1.5, color: C.navy}}>
				РАСПИСАНИЕ
			</div>
			<div style={{height: 1, backgroundColor: C.widgetDivider, margin: '14px 0 8px'}} />
			{rows.map((r) => (
				<div
					key={r.n}
					style={{
						display: 'flex',
						alignItems: 'center',
						gap: 14,
						padding: '10px 12px',
						borderRadius: 16,
						backgroundColor: r.current ? C.widgetHi : 'transparent',
					}}
				>
					<span style={{width: 18 * SP, fontFamily: Golos, fontWeight: 700, fontSize: 13 * SP, color: rowColor(r)}}>
						{r.n}
					</span>
					<span
						style={{
							width: 82 * SP,
							fontFamily: Golos,
							fontWeight: 400,
							fontSize: 12 * SP,
							color: r.current ? C.widgetMain : C.widgetSub,
						}}
					>
						{r.time}
					</span>
					<span style={{flex: 1, fontFamily: Golos, fontWeight: 400, fontSize: 13 * SP, color: rowColor(r)}}>
						{r.subject}
					</span>
					{r.room && (
						<span
							style={{
								fontFamily: Golos,
								fontWeight: 700,
								fontSize: 11 * SP,
								color: C.widgetMain,
								backgroundColor: C.widgetHi,
								borderRadius: 10,
								padding: '3px 14px',
							}}
						>
							{r.room}
						</span>
					)}
				</div>
			))}
		</div>
	);
};

// Виджет «До звонка» — реплика widget_bell_countdown.xml (WidgetStyle.LIGHT).
export const CountdownWidget: React.FC<{from: number; lesson: string}> = ({from, lesson}) => {
	return (
		<div
			style={{
				backgroundColor: '#FFFFFF',
				borderRadius: 36,
				padding: '28px 30px',
				boxShadow: '0 8px 28px rgba(11,53,100,0.14)',
				textAlign: 'center',
			}}
		>
			<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 11 * SP, letterSpacing: 1.5, color: C.widgetSub}}>
				ДО ЗВОНКА
			</div>
			<Seconds from={from} style={{fontSize: 26 * SP, color: C.widgetMain, display: 'block', margin: '6px 0'}} />
			<div style={{fontFamily: Golos, fontWeight: 400, fontSize: 13 * SP, color: C.widgetSub}}>{lesson}</div>
		</div>
	);
};

// Шторка-уведомление Android: карточка #FFFFFF radius 28, иконка приложения,
// строки из реальных текстов NotificationHelper.kt.
export const NotificationCard: React.FC<{
	title: React.ReactNode;
	body?: string;
	when?: string;
	children?: React.ReactNode;
}> = ({title, body, when = 'сейчас', children}) => {
	return (
		<div
			style={{
				backgroundColor: '#FFFFFF',
				borderRadius: 28,
				padding: '24px 30px',
				boxShadow: '0 12px 40px rgba(0,0,0,0.28)',
				boxSizing: 'border-box',
				width: '100%',
			}}
		>
			<div style={{display: 'flex', alignItems: 'center', gap: 14, marginBottom: 12}}>
				{/* иконка приложения = эмблема icon.svg 48×48 на белом круге (vision-QA f350) */}
				<div
					style={{
						width: 64,
						height: 64,
						borderRadius: '50%',
						backgroundColor: '#FFFFFF',
						boxShadow: '0 0 0 2px rgba(17,24,32,0.10)',
						display: 'flex',
						alignItems: 'center',
						justifyContent: 'center',
						flexShrink: 0,
					}}
				>
					<Img src={staticFile('icon.svg')} style={{width: 48, height: 48}} />
				</div>
				<span style={{fontFamily: Golos, fontWeight: 400, fontSize: 28, color: C.widgetSub}}>
					Мой Политех · {when}
				</span>
			</div>
			<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 34, color: C.widgetMain}}>{title}</div>
			{body && (
				<div style={{fontFamily: Golos, fontWeight: 400, fontSize: 31, color: C.widgetSub, marginTop: 4}}>
					{body}
				</div>
			)}
			{children}
		</div>
	);
};

export const Shade: React.FC<{children: React.ReactNode}> = ({children}) => {
	return (
		<div
			style={{
				position: 'absolute',
				inset: 0,
				backgroundColor: 'rgba(17,24,32,0.42)',
				padding: '100px 36px',
				display: 'flex',
				flexDirection: 'column',
				gap: 22,
				boxSizing: 'border-box',
			}}
		>
			{children}
		</div>
	);
};

// Демо-расписание вымышленной группы ИС-99 (п.7.3 — без персональных данных).
export const DEMO_ROWS: WRow[] = [
	{n: 1, time: '8:30–10:00', subject: 'Математика', room: '204', past: true},
	{n: 2, time: '10:10–11:40', subject: 'Информатика', room: '315', current: true},
	{n: 3, time: '12:10–13:40', subject: 'Физика', room: '118'},
	{n: 4, time: '13:50–15:20', subject: 'История', room: '207'},
];
