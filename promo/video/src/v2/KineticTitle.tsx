import React from 'react';
import {Easing, interpolate, useCurrentFrame} from 'remotion';
import {C, Golos} from '../theme';

// KineticTitle v2 (VIDEO_V2_SPEC.md): ВМЕСТО белых плашек — текст прямо на тёмном фоне.
// Заголовок Golos 900 84px #FFF, mask-reveal clip-path inset(100%→0) за 8 кадров НА бит;
// янтарное #FFB020 слово + underline-swipe (rect 10px, scaleX 0→1, 6 кадров) на следующий бит;
// подзаголовок Golos 500 40px #9FB6CC fade+8px за 6 кадров.

const SAFE_X = 96;

// mask-reveal снизу за 8 кадров (ease-out) — переиспользуется в S1/S7 для вордмарков.
export const MaskText: React.FC<{at: number; style?: React.CSSProperties; children: React.ReactNode}> = ({at, style, children}) => {
	const frame = useCurrentFrame();
	const p = interpolate(frame, [at, at + 8], [100, 0], {
		extrapolateLeft: 'clamp',
		extrapolateRight: 'clamp',
		easing: Easing.out(Easing.cubic),
	});
	return <div style={{clipPath: `inset(${p}% 0 0 0)`, ...style}}>{children}</div>;
};

// underline-swipe: rect высота 10px, scaleX 0→1 за 6 кадров от кадра at.
export const Underline: React.FC<{at: number; width?: number | string; style?: React.CSSProperties}> = ({at, width = '100%', style}) => {
	const frame = useCurrentFrame();
	const p = interpolate(frame, [at, at + 6], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	return (
		<div
			style={{
				width,
				height: 10,
				backgroundColor: C.amber,
				transform: `scaleX(${p})`,
				transformOrigin: 'left center',
				...style,
			}}
		/>
	);
};

export const KineticTitle: React.FC<{
	title: string;
	accent?: string;
	sub?: string;
	titleAt?: number; // бит mask-reveal заголовка (кадр сцены)
	accentAt?: number; // следующий бит: янтарное слово + underline-swipe
	subAt?: number;
	left?: number;
	top?: number;
	width?: number;
	align?: 'left' | 'center';
}> = ({title, accent, sub, titleAt = 0, accentAt = 18, subAt, left = SAFE_X, top, width = 430, align = 'left'}) => {
	const frame = useCurrentFrame();
	const subP = interpolate(frame, [subAt ?? accentAt + 9, (subAt ?? accentAt + 9) + 6], [0, 1], {
		extrapolateLeft: 'clamp',
		extrapolateRight: 'clamp',
	});
	return (
		<div style={{position: 'absolute', left, top, width, textAlign: align, fontFamily: Golos, color: '#FFFFFF'}}>
			<MaskText at={titleAt} style={{fontSize: 84, fontWeight: 900, lineHeight: 1.05, paddingTop: 8}}>
				{title}
			</MaskText>
			{accent && (
				<MaskText at={accentAt} style={{fontSize: 84, fontWeight: 900, lineHeight: 1.15, color: C.amber, marginTop: 18}}>
					<span style={{display: 'inline-block'}}>
						{accent}
						{/* underline в потоке: absolute под box'ом срезался бы clip-path'ом mask-reveal */}
						<Underline at={accentAt} style={{display: 'block', marginTop: 8}} />
					</span>
				</MaskText>
			)}
			{sub && (
				<div
					style={{
						fontSize: 40,
						fontWeight: 500,
						lineHeight: 1.25,
						color: '#9FB6CC',
						marginTop: 28,
						opacity: subP,
						translate: `0 ${(1 - subP) * 8}px`,
					}}
				>
					{sub}
				</div>
			)}
		</div>
	);
};
