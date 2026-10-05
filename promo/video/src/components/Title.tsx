import React from 'react';
import {Easing, interpolate, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, Golos} from '../theme';

// Титр-плашка п.4.3: белая rgba .94, radius 24, тень; Golos Text 900; accentWord —
// янтарный #FFB020 на синей подложке #0B3564 (accentBg — темнее для тёмных сцен).
// Вход fade + y 24→0, 300мс (п.4.5). '\n' в text = перенос строки; sub — подтитр 44px 500.
export const Title: React.FC<{
	text: string;
	sub?: string;
	accent?: string;
	accentBg?: string;
	delay?: number;
	align?: 'center' | 'left';
	dot?: boolean; // янтарная точка-пульс на вордмарке (A1), цикл 1200мс sin
}> = ({text, sub, accent, accentBg = C.navy, delay = 0, align = 'center', dot = false}) => {
	const frame = useCurrentFrame();
	const {fps} = useVideoConfig();
	const f = Math.max(0, frame - delay);
	const enter = interpolate(f, [0, 0.3 * fps], [0, 1], {
		extrapolateLeft: 'clamp',
		extrapolateRight: 'clamp',
		easing: Easing.out(Easing.cubic),
	});
	// ponytail: точные тексты сценария длиннее «≤30 симв.» п.4.3 — уменьшаем кегль по длине,
	// чтобы титр оставался ≤4 строк; если закажут правку библии — вернуть 76 везде.
	// С подтитром заголовок держим в 1 строку 58px, чтобы плашка не съедала выноски (vision-QA f120).
	const size = sub ? 58 : text.includes('\n') ? 46 : text.length > 52 ? 46 : text.length > 34 ? 58 : 76;
	const pulse = 0.55 + 0.45 * Math.sin(((frame % (1.2 * fps)) / (1.2 * fps)) * 2 * Math.PI);

	const withAccent = (s: string) => {
		const i = accent ? s.indexOf(accent) : -1;
		if (i < 0) return s;
		return (
			<>
				{s.slice(0, i)}
				<span
					style={{
						backgroundColor: accentBg,
						color: C.amber,
						borderRadius: 12,
						padding: '0 14px',
						whiteSpace: 'nowrap', // не рвать янтарную плашку переносом (vision-QA f250)
					}}
				>
					{accent}
				</span>
				{s.slice(i + accent!.length)}
			</>
		);
	};
	const lines = text.split('\n');

	return (
		<div
			style={{
				opacity: enter,
				translate: `0 ${(1 - enter) * 24}px`,
				backgroundColor: 'rgba(255,255,255,0.94)',
				borderRadius: 24,
				boxShadow: '0 8px 32px rgba(11,53,100,0.18)',
				padding: '36px 48px',
				fontFamily: Golos,
				fontWeight: 900,
				fontSize: size,
				lineHeight: 1.05,
				color: C.ink,
				textAlign: align,
				maxWidth: 888,
			}}
		>
			{lines.map((l, i) => (
				<div key={i}>
					{withAccent(l)}
					{dot && i === lines.length - 1 && !sub && (
						<span
							style={{
								display: 'inline-block',
								width: 28,
								height: 28,
								borderRadius: '50%',
								backgroundColor: C.amber,
								marginLeft: 22,
								verticalAlign: 'middle',
								opacity: pulse,
								scale: `${0.85 + 0.3 * pulse}`,
							}}
						/>
					)}
				</div>
			))}
			{sub && (
				<div
					style={{
						fontFamily: Golos,
						fontWeight: 500,
						fontSize: 44,
						lineHeight: 1.15,
						color: C.sub,
						marginTop: 10,
					}}
				>
					{withAccent(sub)}
					{dot && (
						<span
							style={{
								display: 'inline-block',
								width: 28,
								height: 28,
								borderRadius: '50%',
								backgroundColor: C.amber,
								marginLeft: 22,
								verticalAlign: 'middle',
								opacity: pulse,
								scale: `${0.85 + 0.3 * pulse}`,
							}}
						/>
					)}
				</div>
			)}
		</div>
	);
};
