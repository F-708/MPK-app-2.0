import React from 'react';
import {Img, staticFile} from 'remotion';
import {C} from './theme';

// Минимальный мокап телефона: скруглённая рамка + шот внутри.
// Шот 1648x3660 рендерится на всю ширину экрана; видимое окно ниже по высоте —
// shiftY (px экрана) сдвигает картинку вверх, показывая нижние карточки.
export const PhoneFrame: React.FC<{
	shot: string;
	width: number;
	viewHeight: number; // высота видимого экрана, px
	shiftY?: number;
}> = ({shot, width, viewHeight, shiftY = 0}) => {
	const pad = Math.round(width * 0.032);

	return (
		<div
			style={{
				width: width + pad * 2,
				height: viewHeight + pad * 2,
				borderRadius: Math.round(width * 0.09),
				background: C.ink,
				padding: pad,
				boxShadow: '0 30px 80px rgba(11,53,100,0.35)',
			}}
		>
			<div
				style={{
					width,
					height: viewHeight,
					borderRadius: Math.round(width * 0.055),
					overflow: 'hidden',
					background: C.card,
					position: 'relative',
				}}
			>
				<Img
					src={staticFile(`shots/${shot}`)}
					style={{
						width,
						height: Math.round((width * 3660) / 1648),
						transform: `translateY(${-shiftY}px)`,
					}}
				/>
			</div>
		</div>
	);
};
