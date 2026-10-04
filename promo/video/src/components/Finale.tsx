import React from 'react';
import {Easing, Img, interpolate, staticFile, useCurrentFrame} from 'remotion';
import {C, Golos} from '../theme';

// A10 финал: фон #0B3564 (в SceneWrap), иконка + вордмарк, сатирическая строка
// мелким 24sp #5B6B7A + «Релиз скоро». CTA-плашки НЕТ (скачивать некуда).
export const Finale: React.FC<{have: Record<string, string>}> = ({have}) => {
	const frame = useCurrentFrame();
	const fade = interpolate(frame, [0, 9], [0, 1], {
		extrapolateRight: 'clamp',
		easing: Easing.out(Easing.cubic),
	});
	const rise = (1 - fade) * 24;

	return (
		<div
			style={{
				position: 'absolute',
				inset: 0,
				display: 'flex',
				flexDirection: 'column',
				alignItems: 'center',
				justifyContent: 'center',
				gap: 30,
				opacity: fade,
				padding: '0 96px',
				textAlign: 'center',
			}}
		>
			{have['icon'] ? (
				<Img
					src={staticFile(`shots/icon.${have['icon'] === 'mp4' ? 'png' : have['icon']}`)}
					style={{width: 200, height: 200, borderRadius: 44, translate: `0 ${rise}px`}}
				/>
			) : (
				// TODO-заглушка «иконка»: заменить на реальный icon.png (ассет №12 п.5), когда дадут.
				<div
					style={{
						width: 200,
						height: 200,
						borderRadius: 44,
						backgroundColor: C.blue,
						display: 'flex',
						alignItems: 'center',
						justifyContent: 'center',
						fontFamily: Golos,
						fontWeight: 900,
						fontSize: 120,
						color: '#FFFFFF',
						translate: `0 ${rise}px`,
						boxShadow: '0 20px 60px rgba(0,0,0,0.35)',
					}}
				>
					П
				</div>
			)}
			<div
				style={{
					fontFamily: Golos,
					fontWeight: 900,
					fontSize: 88,
					color: '#FFFFFF',
					translate: `0 ${rise}px`,
				}}
			>
				Мой Политех
			</div>
			<div
				style={{
					fontFamily: Golos,
					fontWeight: 500,
					fontSize: 44,
					lineHeight: 1.15,
					color: 'rgba(255,255,255,0.85)',
					maxWidth: 820,
				}}
			>
				весь колледж в одном приложении
			</div>
			<div
				style={{
					marginTop: 40,
					fontFamily: Golos,
					fontWeight: 400,
					fontSize: 38, // 24sp «мелким» в масштабе кадра 1080
					color: '#5B6B7A',
					maxWidth: 820,
					lineHeight: 1.3,
				}}
			>
				Это трейлер. Скачать пока нельзя — мы сами ещё не выложили.
			</div>
			<div style={{fontFamily: Golos, fontWeight: 500, fontSize: 44, color: '#AFC6E4'}}>
				Релиз скоро
			</div>
		</div>
	);
};
