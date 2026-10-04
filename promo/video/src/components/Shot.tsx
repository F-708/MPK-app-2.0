import React from 'react';
import {Img, OffthreadVideo, staticFile} from 'remotion';
import {C, Golos} from '../theme';

// Кадр приложения из promo/shots/ (синкается в public/shots/ + манифест shots.json).
// Файла нет → серая заглушка с именем кадра; есть .mp4 → OffthreadVideo (скринкаст жеста).
export const Shot: React.FC<{
	name: string;
	have: Record<string, string>;
	style?: React.CSSProperties;
}> = ({name, have, style}) => {
	const ext = have[name];
	const fill: React.CSSProperties = {
		position: 'absolute',
		inset: 0,
		width: '100%',
		height: '100%',
		objectFit: 'cover',
		...style,
	};
	if (ext === 'mp4' || ext === 'webm') {
		return <OffthreadVideo src={staticFile(`shots/${name}.${ext}`)} style={fill} />;
	}
	if (ext === 'png' || ext === 'jpg') {
		return <Img src={staticFile(`shots/${name}.${ext}`)} style={fill} />;
	}
	return (
		<div
			style={{
				...fill,
				background: 'repeating-linear-gradient(-45deg, #D8DEE6 0 28px, #CFD7E1 28px 56px)',
				display: 'flex',
				flexDirection: 'column',
				alignItems: 'center',
				justifyContent: 'center',
				gap: 12,
			}}
		>
			<div style={{fontFamily: Golos, fontWeight: 700, fontSize: 30, color: C.sub, letterSpacing: 2}}>
				КАДР
			</div>
			<div style={{fontFamily: Golos, fontWeight: 500, fontSize: 34, color: C.ink}}>{name}.png</div>
		</div>
	);
};
