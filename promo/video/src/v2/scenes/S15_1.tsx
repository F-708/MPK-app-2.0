import React from 'react';
import {AbsoluteFill, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, Golos} from '../../theme';
import {MaskText} from '../KineticTitle';

// S1 (0–18) Wordmark: «МОЙ ПОЛИТЕХ» kinetic 900 120px по центру, mask-reveal на бите 0;
// янтарная точка-пульс над «И» (1200ms sin = период 36 кадров). Без телефона.
export const S15_1: React.FC = () => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const pulse = 0.55 + 0.45 * Math.sin(((frame % (1.2 * fps)) / (1.2 * fps)) * 2 * Math.PI);
	return (
		<AbsoluteFill style={{justifyContent: 'center', alignItems: 'center'}}>
			<MaskText
				at={0}
				style={{
					fontFamily: Golos,
					fontWeight: 900,
					fontSize: 120,
					lineHeight: 1,
					letterSpacing: 6,
					color: '#FFFFFF',
					paddingTop: 48, // точка над «И» не обрезается клипом
					textAlign: 'center',
				}}
			>
				МОЙ ПОЛ
				<span style={{position: 'relative'}}>
					И
					<span
						style={{
							position: 'absolute',
							top: -34,
							left: '50%',
							translate: '-50% 0',
							width: 26,
							height: 26,
							borderRadius: '50%',
							backgroundColor: C.amber,
							opacity: pulse,
							scale: `${0.85 + 0.3 * pulse}`,
						}}
					/>
				</span>
				ТЕХ
			</MaskText>
		</AbsoluteFill>
	);
};
