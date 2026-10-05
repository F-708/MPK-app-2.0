import React from 'react';
import {AbsoluteFill, Easing, interpolate, useCurrentFrame} from 'remotion';
import {C, Golos, Mono} from '../../theme';
import {KineticTitle} from '../KineticTitle';
import {PhoneDark} from '../PhoneDark';

// S6 (306–378) Домашний экран: реплика home (тёмные обои, статусбар 10:32, виджет Today
// 800px с 4 строками) + push-карточка «до звонка 4:31» выезжает сверху на бите 324 (кадр 18),
// тик секунд на бите 342 (кадр 36). Текст справа: «Живёт» + акцент «на домашнем экране».

// Реплика home — экспортируется для S7 (телефон уезжает вниз с этим же экраном).
export const HomeReplica: React.FC<{pushFrame: number | null}> = ({pushFrame}) => {
	const frame = useCurrentFrame();
	const rows = [
		['09:40', 'Матанализ'],
		['11:20', 'Физика'],
		['13:00', 'Программирование'],
	];
	const drop =
		pushFrame === null
			? 0
			: interpolate(frame, [pushFrame, pushFrame + 10], [-260, 132], {
					extrapolateLeft: 'clamp',
					extrapolateRight: 'clamp',
					easing: Easing.out(Easing.cubic),
				});
	return (
		<AbsoluteFill style={{background: 'radial-gradient(120% 80% at 50% 0%, #14375F 0%, #0A1E3A 60%, #071426 100%)'}}>
			{/* статусбар */}
			<div style={{position: 'absolute', top: 0, left: 0, right: 0, height: 90, display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 44px'}}>
				<span style={{fontFamily: Golos, fontWeight: 500, fontSize: 36, color: '#FFFFFF'}}>10:32</span>
				<svg width={110} height={36} viewBox="0 0 110 36" fill="#FFFFFF">
					<path d="M20 26 L32 8 L44 26 Z" opacity={0.9} />
					<rect x={56} y={12} width={34} height={16} rx={4} opacity={0.9} />
					<rect x={92} y={17} width={5} height={6} rx={2} opacity={0.9} />
				</svg>
			</div>
			{/* виджет Today 800px, 4 строки (LIGHT-палитра приложения) */}
			<div style={{position: 'absolute', left: 16, top: 300, width: 800, backgroundColor: '#F4F6F8', borderRadius: 48, padding: '36px 40px', boxSizing: 'border-box'}}>
				<div style={{fontFamily: Golos, fontWeight: 900, fontSize: 46, color: '#111827'}}>Сегодня</div>
				{rows.map(([t, s], i) => (
					<div key={t} style={{display: 'flex', gap: 24, alignItems: 'baseline', marginTop: 26, borderTop: i === 0 ? 'none' : '1px solid #E2E8F0', paddingTop: i === 0 ? 0 : 22}}>
						<span style={{fontFamily: Mono, fontWeight: 700, fontSize: 38, color: '#111827'}}>{t}</span>
						<span style={{fontFamily: Golos, fontWeight: 500, fontSize: 38, color: '#6B7280'}}>{s}</span>
					</div>
				))}
			</div>
			{/* push-карточка «до звонка» */}
			{pushFrame !== null && frame >= pushFrame && (
				<div
					style={{
						position: 'absolute',
						left: 48,
						top: drop,
						width: 736,
						backgroundColor: '#FFFFFF',
						borderRadius: 36,
						boxShadow: '0 16px 40px rgba(0,0,0,0.35)',
						display: 'flex',
						alignItems: 'center',
						gap: 26,
						padding: '26px 32px',
						boxSizing: 'border-box',
					}}
				>
					<div style={{width: 72, height: 72, borderRadius: '50%', backgroundColor: '#FFF3DC', flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
						<svg width={40} height={40} viewBox="0 0 24 24" fill={C.amber}>
							<path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z" />
						</svg>
					</div>
					<div>
						<div style={{fontFamily: Golos, fontWeight: 500, fontSize: 32, color: '#6B7280'}}>до звонка</div>
						<div style={{fontFamily: Mono, fontWeight: 700, fontSize: 56, color: '#111827', fontVariantNumeric: 'tabular-nums'}}>
							{frame - pushFrame < 18 ? '4:31' : '4:30'}
						</div>
					</div>
				</div>
			)}
		</AbsoluteFill>
	);
};

export const S15_6: React.FC = () => (
	<AbsoluteFill>
		<PhoneDark scale={0.52} left={96} top={561}>
			<HomeReplica pushFrame={18} />
		</PhoneDark>
		<KineticTitle title="Живёт" accent="на домашнем экране" titleAt={0} accentAt={18} left={520} top={640} width={464} />
	</AbsoluteFill>
);
