import React from 'react';
import {useCurrentFrame} from 'remotion';
import {RouteLine, RoomChip} from './Overlay';
import {SceneShell} from './SceneShell';

// Ломаная маршрута — доли экрана; калибровано по QA-кадру f0500 (шот 1648×3660).
const ROUTE: [number, number][] = [
	[0.5, 0.34],
	[0.5, 0.806],
];

export const S4Map: React.FC<{dur?: number}> = ({dur = 165}) => {
	const frame = useCurrentFrame();
	const [ex, ey] = ROUTE[ROUTE.length - 1];
	return (
		<SceneShell
			dur={dur}
			label="НАВИГАЦИЯ"
			line="Планы этажей и поиск нужной аудитории"
			shot="shot_map_route.png"
			scale={[1, 1.38]}
			fx={[0.5, 0.5]}
			fy={[0.5, 0.57]}
			overlays={
				<>
					<RouteLine frame={frame} dur={dur} points={ROUTE} />
					<RoomChip fx={ex} fy={ey - 0.05} frame={frame} at={dur - 45} />
				</>
			}
		/>
	);
};
