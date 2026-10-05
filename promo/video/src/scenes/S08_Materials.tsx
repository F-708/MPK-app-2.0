import React from 'react';
import {Sequence, useVideoConfig} from 'remotion';
import {C, SceneProps} from '../theme';
import {SceneWrap, Stage} from '../components/Layout';
import {Shot} from '../components/Shot';
import {KenBurns} from '../components/KenBurns';
import {Callout} from '../components/Callout';

// A8 (690–780): материалы — список предметов → PDF; посадка contain: скриншот вписан
// ЦЕЛИКОМ, letterbox #F4F6F8 (vision-QA2 f700: «+ ДОБАВИТЬ» не у кромки, орг-шапка не под
// таблеткой, навбар в кадре) → список статичен, лёгкий наезд только на PDF (низ — белый
// margin, тулбар сверху не кроется). Янтарная рамка вокруг области PDF «дописывается».
export const S08: React.FC<SceneProps> = ({have, wide}) => {
	const {fps} = useVideoConfig();
	const fit: React.CSSProperties = {objectFit: 'contain', backgroundColor: C.bg};
	return (
		<SceneWrap bg={C.bg}>
			<Stage
				wide={wide}
				screen={
					<>
						<Sequence durationInFrames={30} premountFor={fps}>
							<Shot name="a8_materials_list" have={have} style={fit} />
						</Sequence>
						<Sequence from={30} premountFor={fps}>
							<KenBurns scale={[1, 1.06]} y={[0, 50]}>
								<Shot name="a8_material_pdf" have={have} style={fit} />
							</KenBurns>
						</Sequence>
					</>
				}
				title="Конспекты и материалы — хранятся в приложении"
				accent="хранятся в приложении"
				titleDelay={0}
				overlay={
					<Sequence from={30}>
						{/* rect пересчитан под contain-посадку (масштаб 0.909, сдвиг x +41) */}
						<Callout delay={12} x={124 + 137} y={84 + 425} rect={{w: 558, h: 702}} />
					</Sequence>
				}
			/>
		</SceneWrap>
	);
};
