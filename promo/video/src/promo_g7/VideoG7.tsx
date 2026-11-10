// PromoG7 — 30s, 9:16, функция «Материалы». Изолированная линия, чужих файлов не трогает.
// Границы сцен: 0/150/360/560/750/900
//  S1 0-150   Hook: «Учебные материалы — в одном месте»
//  S2 150-360 Список предметов с материалами (PDF, фото, заметки)
//  S3 360-560 Открытие PDF прямо в приложении
//  S4 560-750 Выделение текста и пометки в PDF
//  S5 750-900 Финал: «Мой Политех»
import React from 'react';
import {AbsoluteFill, Sequence, interpolate, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, Golos, Mono} from '../theme';

const B = [0, 150, 360, 560, 750, 900];
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

// Иконка документа: лист с загнутым уголком + бейдж типа (PDF / фото / заметка)
const DocIcon: React.FC<{kind: 'pdf' | 'photo' | 'note'; size?: number}> = ({kind, size = 110}) => {
	const bg = kind === 'pdf' ? '#E53935' : kind === 'photo' ? '#2E9E5B' : C.amber;
	const label = kind === 'pdf' ? 'PDF' : null;
	return (
		<div style={{
			width: size, height: size, borderRadius: 24, flexShrink: 0,
			background: '#EEF3F9', display: 'flex', alignItems: 'center', justifyContent: 'center',
			position: 'relative',
		}}>
			{kind === 'pdf' && (
				<svg width={size * 0.56} height={size * 0.56} viewBox="0 0 24 24" fill="none">
					<path d="M6 2h8l4 4v16H6V2z" fill="#FFF" stroke={bg} strokeWidth={1.6} />
					<path d="M14 2l4 4h-4V2z" fill={bg} />
					<text x="12" y="17" textAnchor="middle" fontFamily={Mono} fontWeight={700} fontSize={5.4} fill={bg}>{label}</text>
				</svg>
			)}
			{kind === 'photo' && (
				<svg width={size * 0.56} height={size * 0.56} viewBox="0 0 24 24" fill="none">
					<rect x="3" y="4" width="18" height="16" rx="2" fill="#FFF" stroke={bg} strokeWidth={1.6} />
					<circle cx="9" cy="10" r="2" fill={bg} />
					<path d="M4 18l5-5 3 3 4-4 4 4v2H4v-2z" fill={bg} />
				</svg>
			)}
			{kind === 'note' && (
				<svg width={size * 0.56} height={size * 0.56} viewBox="0 0 24 24" fill="none">
					<rect x="4" y="3" width="16" height="18" rx="2" fill="#FFF" stroke={bg} strokeWidth={1.6} />
					<path d="M8 8h8M8 12h8M8 16h5" stroke={bg} strokeWidth={1.8} strokeLinecap="round" />
				</svg>
			)}
		</div>
	);
};

// Строка предмета: плашка-инициал + название + счётчики материалов
const SubjectRow: React.FC<{name: string; counts: string; tone: string}> = ({name, counts, tone}) => (
	<Card style={{display: 'flex', alignItems: 'center', gap: 28, padding: '26px 32px'}}>
		<div style={{
			width: 110, height: 110, borderRadius: 26, flexShrink: 0,
			background: `linear-gradient(135deg, ${tone} 0%, ${C.navy} 100%)`,
			display: 'flex', alignItems: 'center', justifyContent: 'center',
			fontFamily: Golos, fontWeight: 900, fontSize: 40, color: '#FFF',
		}}>
			{name[0]}
		</div>
		<div style={{flex: 1, minWidth: 0}}>
			<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 40, color: C.ink, lineHeight: 1.15}}>{name}</div>
			<div style={{fontFamily: Golos, fontWeight: 500, fontSize: 30, color: C.sub, marginTop: 6}}>{counts}</div>
		</div>
		<svg width={36} height={36} viewBox="0 0 24 24" fill="none">
			<path d="M9 5l7 7-7 7" stroke="#B9C7D4" strokeWidth={2.8} strokeLinecap="round" strokeLinejoin="round" />
		</svg>
	</Card>
);

// Строка файла внутри предмета
const FileRow: React.FC<{kind: 'pdf' | 'photo' | 'note'; name: string; meta: string}> = ({kind, name, meta}) => (
	<Card style={{display: 'flex', alignItems: 'center', gap: 26, padding: '24px 30px'}}>
		<DocIcon kind={kind} size={96} />
		<div style={{flex: 1, minWidth: 0}}>
			<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 37, color: C.ink, lineHeight: 1.15}}>{name}</div>
			<div style={{fontFamily: Golos, fontWeight: 500, fontSize: 29, color: C.sub, marginTop: 5}}>{meta}</div>
		</div>
	</Card>
);

// Мини-страница PDF: заголовок + строки «текста»
const PdfPage: React.FC<{highlight?: {top: number; left: number; width: number}; progress?: number}> = ({highlight, progress}) => (
	<div style={{
		width: 620, background: '#FFF', borderRadius: 20, padding: '48px 44px',
		boxShadow: '0 16px 50px rgba(11,53,100,0.18)', position: 'relative',
	}}>
		<div style={{width: '55%', height: 30, borderRadius: 8, background: '#D9E2EC', marginBottom: 34}} />
		{[100, 92, 98, 78, 96, 88, 60].map((w, i) => (
			<div key={i} style={{
				width: `${w}%`, height: 22, borderRadius: 7, background: '#E7EDF3', marginBottom: 22,
			}} />
		))}
		{/* выделение поверх строк */}
		{highlight && (
			<div style={{
				position: 'absolute', top: highlight.top, left: highlight.left, width: highlight.width,
				height: 34, borderRadius: 8,
				background: 'rgba(255,176,32,0.45)',
				border: `2px solid ${C.amber}`,
			}} />
		)}
		{progress !== undefined && progress > 0 && (
			<div style={{position: 'absolute', bottom: 14, right: 22, fontFamily: Mono, fontWeight: 700, fontSize: 22, color: C.sub}}>
				{Math.round(progress * 100)}%
			</div>
		)}
	</div>
);

// ---------- S1 hook ----------
const S1: React.FC = () => (
	<AbsoluteFill style={{background: C.bg, justifyContent: 'center', alignItems: 'center', padding: 90}}>
		<FadeSlide from={0} style={{textAlign: 'center'}}>
			<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 34, color: BLUE, letterSpacing: 6, textTransform: 'uppercase', marginBottom: 36}}>
				Мой Политех
			</div>
			<Title size={92}>
				Материалы<br />— всегда под рукой
			</Title>
		</FadeSlide>
		<FadeSlide from={0} delay={18} style={{marginTop: 48, textAlign: 'center'}}>
			<Sub>PDF, фото доски, заметки.<br />По каждому предмету — своё.</Sub>
		</FadeSlide>
		<FadeSlide from={0} delay={30} dy={80} style={{position: 'absolute', bottom: 170, display: 'flex', gap: 40}}>
			<DocIcon kind="pdf" size={140} />
			<DocIcon kind="photo" size={140} />
			<DocIcon kind="note" size={140} />
		</FadeSlide>
	</AbsoluteFill>
);

// ---------- S2 subject list ----------
const S2: React.FC = () => {
	const rows = [
		{name: 'Математика', counts: '3 PDF · 2 фото · 1 заметка', tone: BLUE},
		{name: 'Физика', counts: '2 PDF · 4 фото', tone: '#2E9E5B'},
		{name: 'Программирование', counts: '5 PDF · 3 заметки', tone: C.amber},
		{name: 'История', counts: '1 PDF · 2 фото', tone: '#7A5AF8'},
	];
	return (
		<AbsoluteFill style={{background: C.bg, padding: '130px 70px'}}>
			<FadeSlide from={0}><Title>Материалы</Title></FadeSlide>
			<FadeSlide from={0} delay={6} style={{marginTop: 14}}>
				<Sub size={32}>По предметам · 21 файл</Sub>
			</FadeSlide>
			<div style={{marginTop: 50, display: 'flex', flexDirection: 'column', gap: 26}}>
				{rows.map((r, i) => (
					<FadeSlide key={r.name} from={0} delay={12 + i * 10}>
						<SubjectRow {...r} />
					</FadeSlide>
				))}
			</div>
		</AbsoluteFill>
	);
};

// ---------- S3 open PDF ----------
const S3: React.FC = () => {
	const f = useCurrentFrame();
	// карточка PDF увеличивается и «раскрывается» в страницу
	const open = ease(f, 40, 78);
	const pageScale = interpolate(open, [0, 1], [0.62, 1]);
	const pageOp = ease(f, 46, 66);
	return (
		<AbsoluteFill style={{background: C.bg, padding: '130px 70px'}}>
			<FadeSlide from={0}><Title>Открывается<br />внутри приложения</Title></FadeSlide>

			{/* файл, который открываем */}
			<FadeSlide from={0} delay={10} style={{marginTop: 50, opacity: 1 - open}}>
				<FileRow kind="pdf" name="Лекция 4. Интегралы.pdf" meta="2,4 МБ · 18 страниц" />
			</FadeSlide>

			{/* страница PDF */}
			<div style={{marginTop: 40, display: 'flex', justifyContent: 'center', opacity: pageOp}}>
				<div style={{transform: `scale(${pageScale})`, transformOrigin: 'top center'}}>
					<PdfPage progress={ease(f, 100, 170)} />
				</div>
			</div>

			<FadeSlide from={0} delay={120} style={{marginTop: 30, textAlign: 'center'}}>
				<Sub>Без сторонних читалок</Sub>
			</FadeSlide>
		</AbsoluteFill>
	);
};

// ---------- S4 highlight + note ----------
const S4: React.FC = () => {
	const f = useCurrentFrame();
	// выделение растёт слева направо
	const hl = ease(f, 40, 75);
	const hlWidth = interpolate(hl, [0, 1], [40, 420]);
	return (
		<AbsoluteFill style={{background: C.bg, padding: '130px 70px'}}>
			<FadeSlide from={0}><Title>Выделяй<br />и помечай</Title></FadeSlide>

			<div style={{marginTop: 50, display: 'flex', justifyContent: 'center'}}>
				<PdfPage highlight={{top: 132, left: 44, width: hlWidth}} />
			</div>

			{/* заметка к выделенному */}
			<FadeSlide from={0} delay={90}>
				<Card style={{marginTop: 44, display: 'flex', alignItems: 'flex-start', gap: 24, padding: '30px 34px'}}>
					<DocIcon kind="note" size={90} />
					<div style={{flex: 1}}>
						<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 37, color: C.ink}}>Пометка</div>
						<div style={{fontFamily: Golos, fontWeight: 500, fontSize: 31, color: C.sub, marginTop: 6}}>
							«Пересдать по этой формуле»
						</div>
					</div>
				</Card>
			</FadeSlide>

			<FadeSlide from={0} delay={120} style={{marginTop: 36, textAlign: 'center'}}>
				<Sub>Всё сохраняется рядом с файлом</Sub>
			</FadeSlide>
		</AbsoluteFill>
	);
};

// ---------- S5 final ----------
const S5: React.FC = () => (
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
const SCENES = [S1, S2, S3, S4, S5];

export const VideoG7: React.FC = () => {
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
