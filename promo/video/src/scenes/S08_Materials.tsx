import React from 'react';
import {Sequence, useVideoConfig} from 'remotion';
import {C, SceneProps} from '../theme';
import {SceneWrap, Stage} from '../components/Layout';
import {Shot} from '../components/Shot';
import {KenBurns} from '../components/KenBurns';
import {Callout} from '../components/Callout';

// A8 (690–780): материалы — список предметов → PDF, наезд камеры; янтарный
// штрих-выноска снаружи (рамка вокруг области PDF «дописывается»). Переход cut.
export const S08: React.FC<SceneProps> = ({have, wide}) => {
	const {fps} = useVideoConfig();
	return (
		<SceneWrap bg={C.bg}>
			<Stage
				wide={wide}
				screen={
					<>
						<Sequence durationInFrames={30} premountFor={fps}>
							<KenBurns scale={[1.08, 1.12]} y={[60, -40]}>
								<Shot name="a8_materials_list" have={have} />
							</KenBurns>
						</Sequence>
						<Sequence from={30} premountFor={fps}>
							<KenBurns scale={[1.1, 1.2]} y={[60, -80]}>
								<Shot name="a8_material_pdf" have={have} />
							</KenBurns>
						</Sequence>
					</>
				}
				title="Конспекты и материалы — хранятся в приложении"
				accent="хранятся в приложении"
				overlay={
					<Sequence from={30}>
						<Callout delay={12} x={124 + 106} y={84 + 380} rect={{w: 620, h: 780}} />
					</Sequence>
				}
			/>
		</SceneWrap>
	);
};
