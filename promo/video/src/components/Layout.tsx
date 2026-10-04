import React from 'react';
import {Easing, interpolate, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, Golos, MOCK_9, MOCK_16} from '../theme';
import {PhoneMockup} from './PhoneMockup';
import {Title} from './Title';

export type Trans = 'fade' | 'slide-left' | 'slide-up' | 'none';

// Переходы сцен — внутри сцен (п.6.2): первые/последние 10 кадров (350мс slide / 300мс fade).
export const SceneWrap: React.FC<{
	bg?: string;
	in?: Trans;
	out?: 'fade' | 'none';
	children: React.ReactNode;
}> = ({bg = 'transparent', in: tin = 'none', out = 'none', children}) => {
	const frame = useCurrentFrame();
	const {durationInFrames: dur} = useVideoConfig();
	const T = 10;
	const ease = Easing.inOut(Easing.cubic);
	const enter = interpolate(frame, [0, T], [0, 1], {
		extrapolateLeft: 'clamp',
		extrapolateRight: 'clamp',
		easing: ease,
	});
	const exit = interpolate(frame, [dur - T, dur - 1], [1, 0], {
		extrapolateLeft: 'clamp',
		extrapolateRight: 'clamp',
	});
	return (
		<div
			style={{
				position: 'absolute',
				inset: 0,
				backgroundColor: bg,
				overflow: 'hidden',
				opacity: tin === 'none' ? (out === 'fade' ? exit : 1) : enter * (out === 'fade' ? exit : 1),
				translate:
					tin === 'slide-left'
						? `${(1 - enter) * 90}px 0`
						: tin === 'slide-up'
							? `0 ${(1 - enter) * 90}px`
							: undefined,
			}}
		>
			{children}
		</div>
	);
};

// Раскладка кадра: 9:16 — мокап по центру, титр внизу; 16:9 — мокап в правой трети
// (высота 920), титр слева 55%, водяной знак 8% (п.4.7).
export const Stage: React.FC<{
	wide: boolean;
	screen: React.ReactNode;
	overlay?: React.ReactNode; // выноски в координатах кадра 9:16 (в wide домасштабируются к мокапу)
	title?: string;
	accent?: string;
	titleDelay?: number;
	titleDot?: boolean;
	tilt?: number;
	entrance?: boolean;
	aspect?: number;
}> = ({wide, screen, overlay, title, accent, titleDelay = 6, titleDot = false, tilt = 0, entrance = false, aspect = 2.0}) => {
	const mock = wide ? MOCK_16 : MOCK_9;
	const k = MOCK_16.w / MOCK_9.w;
	return (
		<>
			{wide && (
				<div
					style={{
						position: 'absolute',
						left: 180,
						top: '50%',
						translate: '0 -50%',
						width: 640,
						height: 640,
						borderRadius: '50%',
						border: `44px solid ${C.navy}`,
						opacity: 0.08,
						display: 'flex',
						alignItems: 'center',
						justifyContent: 'center',
						fontFamily: Golos,
						fontWeight: 900,
						fontSize: 380,
						color: C.navy,
					}}
				>
					П
				</div>
			)}
			<div style={{position: 'absolute', left: mock.x, top: mock.y, zIndex: 2}}>
				<PhoneMockup width={mock.w} aspect={aspect} tilt={tilt} entrance={entrance}>
					{screen}
				</PhoneMockup>
			</div>
			{title &&
				(wide ? (
					<div style={{position: 'absolute', left: 96, top: '50%', translate: '0 -50%', zIndex: 3, maxWidth: 900}}>
						<Title text={title} accent={accent} delay={titleDelay} dot={titleDot} align="left" />
					</div>
				) : (
					<div
						style={{
							position: 'absolute',
							left: 96,
							right: 96,
							bottom: 210,
							zIndex: 3,
							display: 'flex',
							justifyContent: 'center',
						}}
					>
						<Title text={title} accent={accent} delay={titleDelay} dot={titleDot} />
					</div>
				))}
			{overlay && (
				<div
					style={{
						position: 'absolute',
						inset: 0,
						zIndex: 4,
						pointerEvents: 'none',
						...(wide
							? {
									transformOrigin: '0 0',
									transform: `translate(${MOCK_16.x - k * MOCK_9.x}px, ${MOCK_16.y - k * MOCK_9.y}px) scale(${k})`,
								}
							: {}),
					}}
				>
					{overlay}
				</div>
			)}
		</>
	);
};
