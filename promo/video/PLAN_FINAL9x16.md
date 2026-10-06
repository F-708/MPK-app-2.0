# PromoFinal9x16 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Финальный промо-ролик «Мой Политех» — композиция `PromoFinal9x16` (1080×1920, 30fps, ровно 900 кадров), 6 сцен: реальные скриншоты в PhoneMockup + кинематографическая камера + дизайн-система приложения.

**Architecture:** Новая папка `src/final/` поверх уже работающих компонентов v1 (`components/PhoneMockup`, `components/Seconds`). Камера — детерминированный контейнер с `translate3d+scale` вокруг центра экрана телефона; оверлеи рисуются в пиксельных координатах шота внутри того же трансформ-контейнера, поэтому всегда совпадают с зумом. Сцены — `<Sequence>` с локальным `useCurrentFrame()`.

**Tech Stack:** Remotion 4.x, React 19, TypeScript strict, `@remotion/google-fonts/Roboto`. Без новых зависимостей.

## Global Constraints

- 900 кадров @30fps = 30.0с; границы сцен (кадры): `0 / 105 / 270 / 420 / 585 / 765 / 900`.
- Никаких CSS transition/keyframes — только `interpolate()` (всегда `extrapolateLeft:'clamp'`, `extrapolateRight:'clamp'`) и `spring()`.
- Картинки только `<Img src={staticFile('shots/...')} />` из `remotion`.
- Палитра: navy `#001737`, brand `#0B3564`, accent `#0072CE`, surface `#F8FAFC`, border `#E2E8F0`, title `#111827`, body `#1F2937`, muted `#6B7280`; бейджи: ДЗ `#0072CE`, Лаб `#8B5CF6`, Экз `#E11D48`.
- Радиус UI-карточек оверлеев СТРОГО 2px. Тень моккапа/плашек: `0 25px 50px -12px rgba(0,0,0,0.35)`.
- Шрифт оверлеев: Roboto (`@remotion/google-fonts/Roboto`); микро-лейблы UPPERCASE, `letterSpacing:'0.15em'`, bold; тон нейтральный, без восклицательных знаков.
- Fade to black: кадры 885–900.
- Коммит после каждого task (репозиторий `D:\MPK app\MPK-app-2.1`).

## Геометрия (справка для всех задач)

- Телефон: `PhoneMockup width=860 aspect=2` в точке `(110,70)` кадра; экран = 832×1664.
- Шоты: 1648×3660 px. `K = 832/1648 = 0.504854`; высота шот-слоя `LAYER_H = 3660*K ≈ 1847.97`; вертикальный кроп при scale 1: `CROP = (LAYER_H-1664)/2 ≈ 91.98`.
- Камера: origin = центр экрана `(416,832)`; фокус `(x,y)` в px шота → `tx = s*(416 - x*K)`, `ty = s*(832 - (y*K - CROP))`.
- Якоря в px шота (вымерены по скриншотам):
  - schedule: карточка текущей пары №3 rect `(66,1918,1517,490)`, центр `(824,2163)`.
  - bells: баннер «СЕЙЧАС» rect `(66,763,1517,194)`; точка шкалы `(1601,1691)`.
  - map: линия маршрута `(825,1244)→(827,2950)`.
  - tasks: бейдж ДЗ rect `(1446,855,100,62)`; бейдж Лаб rect `(1438,1179,112,62)`; центр карточек `(824,1050)`.
  - grades: балл «4.2» rect `(1410,2050,190,150)`, центр `(824,2123)`.

---

### Task 1: Фундамент `src/final/` (theme, Emblem, Cam, Plate)

**Files:**
- Create: `src/final/theme.ts`
- Create: `src/final/Emblem.tsx`
- Create: `src/final/Cam.tsx`
- Create: `src/final/Plate.tsx`

**Interfaces:**
- Produces: `CF`, `F`, `SCR_W`, `SCR_H`, `SHOT_W`, `SHOT_H`, `K`, `LAYER_H`, `CROP`, `CX`, `CY`, `BOUNDS`, `R2`, `BG` (theme); `Emblem({size, fill, style})`; `Cam({keys, frame, children})`, `CamKey{f,s,x,y}`, `ShotLayer({shot, children})`, `ShotSvg({children})`, `ShotDiv({x,y,children,style})`; `Plate({label, text, delay?})`.

- [ ] **Step 1: `src/final/theme.ts`**

```ts
// final — стилевая библия PromoFinal9x16 (1080×1920, 30fps, 900f).
import {loadFont} from '@remotion/google-fonts/Roboto';

export const CF = {
	navy: '#001737', // глубина фона intro/outro
	brand: '#0B3564', // центр радиального градиента
	accent: '#0072CE',
	surface: '#F8FAFC',
	border: '#E2E8F0',
	title: '#111827',
	body: '#1F2937',
	muted: '#6B7280',
	badgeLab: '#8B5CF6',
	badgeExam: '#E11D48',
};

export const F = loadFont('normal', {
	weights: ['400', '500', '700', '900'],
	subsets: ['cyrillic', 'latin'],
}).fontFamily;

// Экран PhoneMockup(860, aspect 2) = 832×1664, стоит в (110,70) кадра.
export const SCR_W = 832;
export const SCR_H = 1664;
// Шоты 1648×3660; слой шота тянется по ширине экрана, вертикаль кропится поровну.
export const SHOT_W = 1648;
export const SHOT_H = 3660;
export const K = SCR_W / SHOT_W;
export const LAYER_H = SHOT_H * K;
export const CROP = (LAYER_H - SCR_H) / 2;
export const CX = SCR_W / 2;
export const CY = SCR_H / 2;

export const BOUNDS = [0, 105, 270, 420, 585, 765, 900];
export const R2 = 2; // радиус UI-карточек — строго 2px (RoundedCornerShape(2.dp))
export const BG = `radial-gradient(1200px 1200px at 50% 32%, ${CF.brand} 0%, ${CF.navy} 72%)`;
export const DEPTH_SHADOW = '0 25px 50px -12px rgba(0, 0, 0, 0.35)';
```

- [ ] **Step 2: `src/final/Emblem.tsx`** (эмблема колледжа = пути из `app/src/main/res/drawable/ic_mpk_emblem.xml`, заливка параметром — на тёмном фоне нужна светлая)

```tsx
// Эмблема МГПК: пути 1:1 из ic_mpk_emblem.xml (viewBox 108), цвет — параметр.
import React from 'react';

const PATHS = [
	'M48,22 C48,20 60,20 60,22 L60,24 L48,24 Z',
	'M44,25 L64,25 L64,28 L44,28 Z',
	'M39,29 L69,29 L69,32 L39,32 Z',
	'M34,33 L74,33 L74,36 L34,36 Z',
	'M24,38 L84,38 L84,42 L24,42 Z',
	'M26,43 L82,43 L82,45 L26,45 Z',
	'M25,46 L31,46 L31,48 L25,48 Z M26,48 L30,48 L30,73 L26,73 Z M25,73 L31,73 L31,75 L25,75 Z',
	'M35,46 L41,46 L41,48 L35,48 Z M36,48 L40,48 L40,73 L36,73 Z M35,73 L41,73 L41,75 L35,75 Z',
	'M45,46 L51,46 L51,48 L45,48 Z M46,48 L50,48 L50,73 L46,73 Z M45,73 L51,73 L51,75 L45,75 Z',
	'M57,46 L63,46 L63,48 L57,48 Z M58,48 L62,48 L62,73 L58,73 Z M57,73 L63,73 L63,75 L57,75 Z',
	'M67,46 L73,46 L73,48 L67,48 Z M68,48 L72,48 L72,73 L68,73 Z M67,73 L73,73 L73,75 L67,75 Z',
	'M77,46 L83,46 L83,48 L77,48 Z M78,48 L82,48 L82,73 L78,73 Z M77,73 L83,73 L83,75 L77,75 Z',
	'M23,76 L85,76 L85,79 L23,79 Z',
	'M20,80 L88,80 L88,83 L20,83 Z',
	'M17,84 L91,84 L91,87 L17,87 Z',
];

export const Emblem: React.FC<{size: number; fill: string; style?: React.CSSProperties}> = ({
	size,
	fill,
	style,
}) => (
	<svg viewBox="0 0 108 108" width={size} height={size} style={style}>
		{PATHS.map((d, i) => (
			<path key={i} d={d} fill={fill} />
		))}
	</svg>
);
```

- [ ] **Step 3: `src/final/Cam.tsx`** (камера + шот-слой; оверлеи внутри делят трансформ камеры)

```tsx
// Cam — детерминированная камера: ключи {f,s,x,y}, x/y — фокус в px шота.
// transform: translate3d+scale вокруг центра экрана; дети (ShotLayer) едут вместе.
import React from 'react';
import {Easing, Img, interpolate, staticFile} from 'remotion';
import {CROP, CX, CY, K, LAYER_H, SCR_W, SHOT_H, SHOT_W} from './theme';

export type CamKey = {f: number; s: number; x: number; y: number};

const at = (keys: CamKey[], frame: number, get: (k: CamKey) => number) => {
	const last = keys[keys.length - 1];
	if (frame <= keys[0].f) return get(keys[0]);
	if (frame >= last.f) return get(last);
	for (let i = 0; i < keys.length - 1; i++) {
		const a = keys[i];
		const b = keys[i + 1];
		if (frame >= a.f && frame <= b.f) {
			const t = (frame - a.f) / (b.f - a.f);
			return get(a) + (get(b) - get(a)) * Easing.inOut(Easing.cubic)(t);
		}
	}
	return get(last);
};

export const Cam: React.FC<{keys: CamKey[]; frame: number; children: React.ReactNode}> = ({
	keys,
	frame,
	children,
}) => {
	const s = at(keys, frame, (k) => k.s);
	const px = at(keys, frame, (k) => k.x) * K;
	const py = at(keys, frame, (k) => k.y) * K - CROP;
	return (
		<div style={{position: 'absolute', inset: 0, overflow: 'hidden'}}>
			<div
				style={{
					position: 'absolute',
					left: 0,
					top: -CROP,
					width: SCR_W,
					height: LAYER_H,
					transform: `translate3d(${s * (CX - px)}px, ${s * (CY - py)}px, 0) scale(${s})`,
					transformOrigin: `${CX}px ${CY}px`,
				}}
			>
				{children}
			</div>
		</div>
	);
};

// Слой шота: Img в полный размер слоя; оверлеи — дети в координатах шота.
export const ShotLayer: React.FC<{shot: string; children?: React.ReactNode}> = ({shot, children}) => (
	<div style={{position: 'absolute', inset: 0}}>
		<Img src={staticFile(`shots/${shot}.png`)} style={{width: '100%', height: '100%', display: 'block'}} />
		{children}
	</div>
);

// SVG-оверлей в координатах шота (1648×3660).
export const ShotSvg: React.FC<{children: React.ReactNode}> = ({children}) => (
	<svg
		viewBox={`0 0 ${SHOT_W} ${SHOT_H}`}
		style={{position: 'absolute', inset: 0, width: '100%', height: '100%', overflow: 'visible'}}
	>
		{children}
	</svg>
);

// Div-оверлей: x,y — точка в px шота (левый верх), дальше обычные screen-px.
export const ShotDiv: React.FC<{
	x: number;
	y: number;
	children: React.ReactNode;
	style?: React.CSSProperties;
}> = ({x, y, children, style}) => (
	<div style={{position: 'absolute', left: x * K, top: y * K, ...style}}>{children}</div>
);
```

- [ ] **Step 4: `src/final/Plate.tsx`** (нижняя плашка-оверлей: карточка приложения, radius 2)

```tsx
// Plate — оверлей сцены: микро-лейбл UPPERCASE 0.15em + нейтральный заголовок.
import React from 'react';
import {Easing, interpolate, useCurrentFrame, useVideoConfig} from 'remotion';
import {CF, DEPTH_SHADOW, F, R2} from './theme';

export const Plate: React.FC<{label: string; text: string; delay?: number}> = ({
	label,
	text,
	delay = 12,
}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	if (frame < delay) return null;
	const t = interpolate(frame - delay, [0, 0.4 * fps], [0, 1], {
		extrapolateLeft: 'clamp',
		extrapolateRight: 'clamp',
		easing: Easing.out(Easing.cubic),
	});
	return (
		<div
			style={{
				position: 'absolute',
				left: 72,
				right: 72,
				bottom: 110,
				zIndex: 5,
				opacity: t,
				translate: `0 ${(1 - t) * 28}px`,
				backgroundColor: CF.surface,
				border: `1px solid ${CF.border}`,
				borderRadius: R2,
				padding: '26px 34px',
				boxShadow: DEPTH_SHADOW,
			}}
		>
			<div style={{fontFamily: F, fontWeight: 700, fontSize: 30, letterSpacing: '0.15em', color: CF.accent}}>
				{label}
			</div>
			<div style={{fontFamily: F, fontWeight: 500, fontSize: 40, lineHeight: 1.25, color: CF.title, marginTop: 8}}>
				{text}
			</div>
		</div>
	);
};
```

- [ ] **Step 5: Проверка типов**

Run: `npx tsc --noEmit`
Expected: без ошибок.

- [ ] **Step 6: Коммит**

```bash
git add promo/video/src/final
git commit -m "feat(promo): final 9x16 foundation — theme, emblem, camera, plate"
```

---

### Task 2: Сцены F1–F6, `VideoFinal`, регистрация в Root

**Files:**
- Create: `src/final/scenes.tsx`
- Create: `src/final/VideoFinal.tsx`
- Modify: `src/Root.tsx` (добавить `<Composition id="PromoFinal9x16">`)

**Interfaces:**
- Consumes: всё из Task 1; `PhoneMockup` из `../components/PhoneMockup`; `Seconds` из `../components/Seconds`.
- Produces: `VideoFinal` (без props).

- [ ] **Step 1: `src/final/scenes.tsx`**

```tsx
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
		frame < 30
			? 0
			: spring({frame: frame - 30, fps, config: {damping: 14, mass: 0.8, stiffness: 100}, durationInFrames: 60});
	const emb = spring({frame, fps, config: {damping: 14, mass: 0.8, stiffness: 100}, durationInFrames: 40});
	const outT = interpolate(frame, [38, 62], [1, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
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
						{f: 70, s: 1.3, x: 824, y: 2163},
						{f: 165, s: 1.34, x: 824, y: 2163},
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
						{f: 45, s: 1.32, x: 824, y: 1150},
						{f: 150, s: 1.38, x: 824, y: 1080},
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
	const reveal = interpolate(frame, [30, 110], [0, 1], {
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
										letterSpacing: '0.08em',
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
							{f: 85, s: 1.3, x: 824, y: 1050},
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
							{f: 85, s: 1.22, x: 824, y: 2123},
							{f: 180, s: 1.32, x: 824, y: 2123},
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
```

- [ ] **Step 2: `src/final/VideoFinal.tsx`**

```tsx
// PromoFinal9x16 — master 30.0s = 900f @30fps. Границы 0/105/270/420/585/765/900.
import React from 'react';
import {AbsoluteFill, Audio, Sequence, staticFile, useCurrentFrame} from 'remotion';
import {interpolate} from 'remotion';
import {F1, F2, F3, F4, F5, F6} from './scenes';
import {CF} from './theme';

export const VideoFinal: React.FC = () => {
	const frame = useCurrentFrame();
	const black = interpolate(frame, [885, 900], [0, 1], {
		extrapolateLeft: 'clamp',
		extrapolateRight: 'clamp',
	});
	return (
		<AbsoluteFill style={{backgroundColor: CF.navy}}>
			<Sequence durationInFrames={105}>
				<F1 />
			</Sequence>
			<Sequence from={105} durationInFrames={165}>
				<F2 />
			</Sequence>
			<Sequence from={270} durationInFrames={150}>
				<F3 />
			</Sequence>
			<Sequence from={420} durationInFrames={165}>
				<F4 />
			</Sequence>
			<Sequence from={585} durationInFrames={180}>
				<F5 />
			</Sequence>
			<Sequence from={765} durationInFrames={135}>
				<F6 />
			</Sequence>
			<AbsoluteFill style={{backgroundColor: '#000000', opacity: black, pointerEvents: 'none'}} />
			<Audio src={staticFile('teaser_v3_30s_90bpm.wav')} />
		</AbsoluteFill>
	);
};
```

- [ ] **Step 3: `src/Root.tsx`** — добавить импорт и композицию после `Promo15v3`:

```tsx
import {VideoFinal} from './final/VideoFinal';
...
			{/* FINAL: 30s master 9:16 по PLAN_FINAL9x16.md */}
			<Composition id="PromoFinal9x16" component={VideoFinal} width={1080} height={1920} fps={30} durationInFrames={900} />
```

- [ ] **Step 4: Проверка типов + контрольные кадры сцен 1–4**

Run: `npx tsc --noEmit && npx remotion still PromoFinal9x16 out/qa/f060.png --frame=60 && npx remotion still PromoFinal9x16 out/qa/f180.png --frame=180 && npx remotion still PromoFinal9x16 out/qa/f340.png --frame=340 && npx remotion still PromoFinal9x16 out/qa/f500.png --frame=500`
Expected: без ошибок tsc; 4 PNG в `out/qa/`.

- [ ] **Step 5: Коммит**

```bash
git add promo/video/src/final promo/video/src/Root.tsx
git commit -m "feat(promo): PromoFinal9x16 — six scenes, camera, outro"
```

---

### Task 3: QA-обвязка, прогон, фиксы читаемости

**Files:**
- Create: `src/final/copy.json`
- Modify: `scripts/check-readability.mjs` (добавить гейт по final-копи, построчно)
- Modify: `package.json` (скрипт `qa` → кадры PromoFinal9x16)

- [ ] **Step 1: `src/final/copy.json`**

```json
{
	"titleIn": 15,
	"scenes": [
		{"id": "F1", "dur": 105, "title": null},
		{"id": "F2", "dur": 165, "title": ["РАСПИСАНИЕ", "Пары, аудитории и подгруппы офлайн"]},
		{"id": "F3", "dur": 150, "title": ["ОТСЧЁТ ДО ЗВОНКА", "Точное время до перемены на экране и в шторке"]},
		{"id": "F4", "dur": 165, "title": ["НАВИГАЦИЯ", "Планы этажей и поиск нужной аудитории"]},
		{"id": "F5", "dur": 180, "title": ["УЧЁБА В ОДНОМ МЕСТЕ", "Задания, конспекты и средний балл"]},
		{"id": "F6", "dur": 135, "title": null}
	]
}
```

- [ ] **Step 2: `scripts/check-readability.mjs`** — дописать после блока v3 (гейт final: каждая строка плашки живёт ≥ (len/15+1)s):

```js
const fin = JSON.parse(readFileSync(new URL('../src/final/copy.json', import.meta.url)));
for (const s of fin.scenes)
	if (s.title)
		for (const line of s.title) {
			const on = s.dur - fin.titleIn;
			const need = (line.length / 15 + 1.0) * 30;
			const ok = on >= need;
			if (!ok) fail++;
			console.log(
				`${ok ? 'PASS' : 'FAIL'} ${s.id.padEnd(8)} line=${String(line.length).padStart(2)} on=${String(on).padStart(3)}f need≥${need.toFixed(1)}f  «${line}»`,
			);
		}
```

- [ ] **Step 3: `package.json`** — заменить скрипт `qa`:

```json
"qa": "npm run sync && remotion still PromoFinal9x16 out/qa/f060.png --frame=60 && remotion still PromoFinal9x16 out/qa/f180.png --frame=180 && remotion still PromoFinal9x16 out/qa/f300.png --frame=300 && remotion still PromoFinal9x16 out/qa/f345.png --frame=345 && remotion still PromoFinal9x16 out/qa/f470.png --frame=470 && remotion still PromoFinal9x16 out/qa/f640.png --frame=640 && remotion still PromoFinal9x16 out/qa/f700.png --frame=700 && remotion still PromoFinal9x16 out/qa/f820.png --frame=820 && remotion still PromoFinal9x16 out/qa/f895.png --frame=895"
```

- [ ] **Step 4: Прогон QA**

Run: `npx tsc --noEmit && npm run qa && npm run qa3`
Expected: tsc чисто; 9 PNG в `out/qa/`; `[readability] PASS` по обоим блокам.

- [ ] **Step 5: Визуальная сверка кадров** — открыть `out/qa/f060…f895`, проверить: эмблема/заголовок читаемы (f060), окантовка карточки на месте (f180), чип отсчёта и точка шкалы (f300/f345), маршрут+маркер (f470), бейджи/балл (f640/f700), финальная плашка (f820), почти чёрный кадр (f895). Найденные расхождения координат править в `scenes.tsx` (якоря в шапке плана).

- [ ] **Step 6: Коммит + отчёт**

```bash
git add promo/video
git commit -m "feat(promo): final QA gate — copy table, readability, qa stills"
```
Отчёт о готовности — в `_reports/2026-10-07-promo-final9x16.md` (список кадров, результаты qa/qa3/tsc).

---

## Self-Review

- Спека: все 6 сцен с указанными кадрами/оверлеями — Tasks 2; палитра/радиус 2/шрифт/лейблы — Global Constraints + theme/Plate; spring damping14/mass0.8 — F1; dashoffset маршрута и окантовки — F2/F4; пульс точки — F3; сплит tasks→grades — F5; zoom-out + плашка + fade 885–900 — F6/VideoFinal; `<Img staticFile>` — ShotLayer; `translate3d+scale` — Cam; clamp во всех interpolate — везде.
- QA: tsc/qa/qa3/out-qa — Task 3; финальный рендер MP4 НЕ запускаем (по спеке).
- Типы: `CamKey`, `Plate`, `ShotDiv/ShotSvg/ShotLayer`, `Emblem` — сигнатуры совпадают во всех задачах.
