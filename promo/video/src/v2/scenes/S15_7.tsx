import React from 'react';
import {AbsoluteFill, Easing, Img, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, Golos} from '../../theme';
import {MaskText, Underline} from '../KineticTitle';
import {PhoneDark} from '../PhoneDark';
import {HomeReplica} from './S15_6';

// S7 (378–450) Финал: телефон уезжает вниз 8 кадров; эмблема icon.svg pop-in spring 160px;
// «Мой Политех» 900 96px mask-reveal; строка 40px #93A6B8 «Трейлер. Релиз — скоро.»;
// янтарный underline-swipe под вордмарком; fade to #082647 за последние 10 кадров.
export const S15_7: React.FC = () => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const exit = interpolate(frame, [0, 8], [0, 1400], {
		extrapolateLeft: 'clamp',
		extrapolateRight: 'clamp',
		easing: Easing.in(Easing.quad),
	});
	const pop = spring({frame: frame - 10, fps, config: {damping: 12}});
	const subP = interpolate(frame, [24, 30], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	const fade = interpolate(frame, [62, 72], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
	return (
		<AbsoluteFill>
			<PhoneDark scale={0.52} left={96} top={561} style={{translate: `0 ${exit}px`}}>
				<HomeReplica pushFrame={null} />
			</PhoneDark>
			<AbsoluteFill style={{justifyContent: 'center', alignItems: 'center'}}>
				{/* эмблема: icon.svg тёмный по рождению → инвертируем в тёплый кремовый на тёмной сцене */}
				<Img
					src={staticFile('icon.svg')}
					width={160}
					height={160}
					style={{filter: 'invert(1)', opacity: pop, transform: `scale(${interpolate(pop, [0, 1], [0.5, 1])})`, marginBottom: 56}}
				/>
				<MaskText at={18} style={{fontFamily: Golos, fontWeight: 900, fontSize: 96, lineHeight: 1.05, color: '#FFFFFF', textAlign: 'center', paddingTop: 12}}>
					Мой Политех
				</MaskText>
				<Underline at={36} width={560} style={{marginTop: 26}} />
				<div
					style={{
						fontFamily: Golos,
						fontWeight: 500,
						fontSize: 40,
						color: '#93A6B8',
						marginTop: 44,
						opacity: subP,
						translate: `0 ${(1 - subP) * 8}px`,
					}}
				>
					Трейлер. Релиз — скоро.
				</div>
			</AbsoluteFill>
			<AbsoluteFill style={{backgroundColor: '#082647', opacity: fade}} />
		</AbsoluteFill>
	);
};
