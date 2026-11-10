// PromoB2 — «Карта колледжа»: кампус → 3D → план этажа → маршрут. 900 кадров.
import React from 'react';
import {
	AbsoluteFill,
	Easing,
	Img,
	Sequence,
	interpolate,
	spring,
	staticFile,
	useCurrentFrame,
	useVideoConfig,
} from 'remotion';
import {PhoneMockup} from '../components/PhoneMockup';
import {BG_B, CB, CROPB, FB, KB, LAYER_HB, SCR_H, SCR_W, SHOT_H, SHOT_W} from './theme';

// ---------- общие кирпичи ----------

// Шот, вписанный по ширине экрана, с плавным зумом/панорамой.
// focus: [x,y] в px шота — точка, в которую смотрим; scale множит KB.
const ZoomShot: React.FC<{
	shot: string;
	scale: number;
	fx: number; // focus x в px шота
	fy: number;
	children?: React.ReactNode; // оверлей в координатах шота (SVG)
}> = ({shot, scale, fx, fy, children}) => {
	// Слой расположен с top=-CROPB (вписан по ширине, кроп по центру).
	// Позиция фокуса в КООРДИНАТАХ ЭКРАНА при scale=1: (fx*KB, fy*KB - CROPB).
	// Origin в центре экрана; чтобы фокус попал в центр, сдвигаем слой на (центр - фокусЭкрана*scale).
	// Фокус в координатах слоя:
	const lx = fx * KB;
	const ly = fy * KB;
	// Слой позиционирован top=-CROPB; после translate(dx,dy)+scale(вокруг 0,0) фокус слоя
	// ложится в (dx + lx*scale, -CROPB + dy + ly*scale). Приравниваем к центру экрана:
	const dx = SCR_W / 2 - lx * scale;
	const dy = SCR_H / 2 + CROPB - ly * scale;
	return (
		<div style={{position: 'absolute', inset: 0, overflow: 'hidden'}}>
			<div
				style={{
					position: 'absolute',
					left: 0,
					top: -CROPB,
					width: SCR_W,
					height: LAYER_HB,
					transform: `translate3d(${dx}px, ${dy}px, 0) scale(${scale})`,
					transformOrigin: '0 0',
				}}
			>
				<Img
					src={staticFile(`shots/${shot}.png`)}
					style={{width: '100%', height: '100%', display: 'block'}}
				/>
				<svg
					viewBox={`0 0 ${SHOT_W} ${SHOT_H}`}
					style={{
						position: 'absolute',
						inset: 0,
						width: '100%',
						height: '100%',
						overflow: 'visible',
					}}
				>
					{children}
				</svg>
			</div>
		</div>
	);
};

// Телефон по центру кадра 1080×1920.
const Stage: React.FC<{children: React.ReactNode}> = ({children}) => (
	<AbsoluteFill style={{background: BG_B, alignItems: 'center', justifyContent: 'center'}}>
		<PhoneMockup width={860} aspect={2.0}>
			{children}
		</PhoneMockup>
	</AbsoluteFill>
);

// Заголовок поверх сцены. plate=true — на полупрозрачной плашке (читается поверх плана).
const Cap: React.FC<{text: string; sub?: string; top?: number; plate?: boolean}> = ({
	text,
	sub,
	top = 1320,
	plate = false,
}) => (
	<div
		style={{
			position: 'absolute',
			left: 0,
			right: 0,
			top,
			textAlign: 'center',
			fontFamily: FB,
			zIndex: 30,
		}}
	>
		<div
			style={
				plate
					? {
							display: 'inline-block',
							background: 'rgba(11,53,100,0.82)',
							borderRadius: 24,
							padding: '26px 44px',
							boxShadow: '0 12px 32px rgba(0,0,0,0.25)',
					  }
					: undefined
			}
		>
			<div style={{fontSize: 64, fontWeight: 900, color: '#FFFFFF', lineHeight: 1.1}}>{text}</div>
			{sub ? (
				<div style={{marginTop: 14, fontSize: 34, fontWeight: 500, color: '#D7E3F0'}}>{sub}</div>
			) : null}
		</div>
	</div>
);

const ease = (t: number) => Easing.inOut(Easing.cubic)(t);
const seg = (frame: number, from: number, to: number) =>
	ease(Math.min(1, Math.max(0, (frame - from) / (to - from))));

// ---------- сцены ----------

// S1 (0-90): интро-тайтл.
const S1Intro: React.FC = () => {
	const f = useCurrentFrame();
	const {fps} = useVideoConfig();
	const s = spring({frame: f, fps, config: {damping: 200, stiffness: 110}, durationInFrames: 24});
	return (
		<AbsoluteFill
			style={{background: BG_B, alignItems: 'center', justifyContent: 'center', fontFamily: FB}}
		>
			<div style={{textAlign: 'center', opacity: s, transform: `translateY(${(1 - s) * 60}px)`}}>
				<div
					style={{
						fontSize: 30,
						fontWeight: 700,
						letterSpacing: 6,
						color: '#7FA8D0',
						textTransform: 'uppercase',
					}}
				>
					Мой Политех
				</div>
				<div
					style={{
						marginTop: 26,
						fontSize: 96,
						fontWeight: 900,
						color: '#FFFFFF',
						lineHeight: 1.05,
					}}
				>
					Карта
					<br />
					колледжа
				</div>
				<div style={{marginTop: 30, fontSize: 38, fontWeight: 500, color: '#C9D6E4'}}>
					Векторный план и маршруты
				</div>
			</div>
		</AbsoluteFill>
	);
};

// S2 (90-240): кампус — зум от общего плана к зданию. Фокус примерно в центре шота
// (здание белым прямоугольником в середине кадра 360×780 → ~(180, 490)).
const S2Campus: React.FC = () => {
	const f = useCurrentFrame();
	const t = seg(f, 8, 128);
	const scale = interpolate(t, [0, 1], [1.0, 2.1]);
	const fy = interpolate(t, [0, 1], [560, 500]);
	return (
		<Stage>
			<ZoomShot shot="b2_campus" scale={scale} fx={180} fy={fy} />
			<Cap text="Весь кампус" sub="Здание, газон, стадион — в одном плане" top={1430} plate />
		</Stage>
	);
};

// S3 (240-360): 3D-наклон кампуса, лёгкий дрейф.
const S3Tilt: React.FC = () => {
	const f = useCurrentFrame();
	const t = seg(f, 6, 108);
	const scale = interpolate(t, [0, 1], [1.15, 1.45]);
	const fx = interpolate(t, [0, 1], [180, 200]);
	return (
		<Stage>
			<ZoomShot shot="b2_campus3d" scale={scale} fx={fx} fy={500} />
			<Cap text="3D-наклон" sub="План читается объёмно" top={1430} plate />
		</Stage>
	);
};

// S4 (360-540): зум в план этажа. В шоте b2_route план занимает ≈y 380..620 (центр ~180,500).
// Заголовок выносим ВНЕ мокапа, вниз кадра — чтобы не перекрывать план.
const S4Floor: React.FC = () => {
	const f = useCurrentFrame();
	const t = seg(f, 4, 150);
	const scale = interpolate(t, [0, 1], [1.0, 2.3]);
	return (
		<Stage>
			<ZoomShot shot="b2_route" scale={scale} fx={180} fy={500} />
			<Cap text="План этажа" sub="Стены, лестницы, двери — векторно" top={1430} plate />
		</Stage>
	);
};

// S5 (540-750): маршрут с бегущими шевронами поверх плана.
// План в шоте b2_route — ≈y 380..620; путь от лестницы (нижний левый) к кабинету 128.
const ROUTE: Array<[number, number]> = [
	[146, 588],
	[172, 566],
	[188, 542],
	[198, 520],
	[206, 502],
];
const S5Route: React.FC = () => {
	const f = useCurrentFrame();
	const scale = 2.3;
	// Прогресс рисования пути.
	const draw = seg(f, 8, 70);
	// Полилиния маршрута.
	const pts = ROUTE.map(([x, y]) => `${x},${y}`).join(' ');
	// Длина сегментов для шевронов.
	const segs: Array<{x: number; y: number; a: number}> = [];
	for (let i = 0; i < ROUTE.length - 1; i++) {
		const [x1, y1] = ROUTE[i];
		const [x2, y2] = ROUTE[i + 1];
		const a = (Math.atan2(y2 - y1, x2 - x1) * 180) / Math.PI;
		const len = Math.hypot(x2 - x1, y2 - y1);
		const n = Math.max(1, Math.floor(len / 14));
		for (let j = 0; j < n; j++) {
			const t = j / n;
			segs.push({x: x1 + (x2 - x1) * t, y: y1 + (y2 - y1) * t, a});
		}
	}
	// Бегущий сдвиг шевронов вдоль маршрута.
	const run = (f * 0.9) % 14;
	return (
		<Stage>
			<ZoomShot shot="b2_route" scale={scale} fx={180} fy={505}>
				{/* линия маршрута */}
				<polyline
					points={pts}
					fill="none"
					stroke={CB.blue}
					strokeWidth={5}
					strokeLinecap="round"
					strokeLinejoin="round"
					opacity={0.9}
					pathLength={1}
					strokeDasharray={1}
					strokeDashoffset={1 - draw}
				/>
				{/* шевроны */}
				{segs.map((p, i) => {
					const local = (i / segs.length) * 1;
					const vis = draw >= local ? 1 : 0;
					return (
						<g
							key={i}
							transform={`translate(${p.x + Math.cos((p.a * Math.PI) / 180) * run} ${
								p.y + Math.sin((p.a * Math.PI) / 180) * run
							}) rotate(${p.a})`}
							opacity={vis}
						>
							<path
								d="M -5 -6 L 3 0 L -5 6"
								fill="none"
								stroke="#FFFFFF"
								strokeWidth={3.4}
								strokeLinecap="round"
								strokeLinejoin="round"
							/>
						</g>
					);
				})}
				{/* точка-цель */}
				<circle cx={ROUTE[4][0]} cy={ROUTE[4][1]} r={9} fill={CB.blue} opacity={draw} />
				<circle cx={ROUTE[4][0]} cy={ROUTE[4][1]} r={4} fill="#FFFFFF" opacity={draw} />
			</ZoomShot>
			<Cap text="Маршрут до кабинета" sub="Шевроны ведут по коридору" top={1430} plate />
		</Stage>
	);
};

// S6 (750-900): финал «Мой Политех».
const S6Finale: React.FC = () => {
	const f = useCurrentFrame();
	const {fps} = useVideoConfig();
	const s = spring({frame: f, fps, config: {damping: 200, stiffness: 100}, durationInFrames: 26});
	return (
		<AbsoluteFill
			style={{background: BG_B, alignItems: 'center', justifyContent: 'center', fontFamily: FB}}
		>
			<div style={{textAlign: 'center', opacity: s, transform: `scale(${0.92 + s * 0.08})`}}>
				<div style={{fontSize: 110, fontWeight: 900, color: '#FFFFFF', letterSpacing: -1}}>
					Мой Политех
				</div>
				<div
					style={{
						margin: '34px auto 0',
						width: 160,
						height: 6,
						borderRadius: 3,
						background: CB.blue,
					}}
				/>
				<div style={{marginTop: 34, fontSize: 38, fontWeight: 500, color: '#C9D6E4'}}>
					Карта колледжа — уже в приложении
				</div>
			</div>
		</AbsoluteFill>
	);
};

// ---------- сборка ----------

export const VideoB2: React.FC = () => (
	<AbsoluteFill style={{backgroundColor: CB.navy}}>
		<Sequence from={0} durationInFrames={90}>
			<S1Intro />
		</Sequence>
		<Sequence from={90} durationInFrames={150}>
			<S2Campus />
		</Sequence>
		<Sequence from={240} durationInFrames={120}>
			<S3Tilt />
		</Sequence>
		<Sequence from={360} durationInFrames={180}>
			<S4Floor />
		</Sequence>
		<Sequence from={540} durationInFrames={210}>
			<S5Route />
		</Sequence>
		<Sequence from={750} durationInFrames={150}>
			<S6Finale />
		</Sequence>
	</AbsoluteFill>
);
