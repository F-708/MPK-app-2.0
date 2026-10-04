import React from 'react';
import {useCurrentFrame, useVideoConfig} from 'remotion';
import {Mono} from '../theme';

// Тикающий mm:ss (JetBrains Mono 700, п.4.2) — детерминированно, не зависит от записи (п.6.3).
export const Seconds: React.FC<{from: number; style?: React.CSSProperties}> = ({from, style}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const left = Math.max(0, Math.ceil(from - frame / fps));
	return (
		<span style={{fontFamily: Mono, fontWeight: 700, fontVariantNumeric: 'tabular-nums', ...style}}>
			{Math.floor(left / 60)}:{String(left % 60).padStart(2, '0')}
		</span>
	);
};
