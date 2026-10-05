import React from 'react';
import {C, SceneProps} from '../theme';
import {SceneWrap} from '../components/Layout';
import {MysteryCards} from '../components/MysteryCards';
import {Title} from '../components/Title';

// A9 (780–840): загадка — три карточки-силуэта под чёрными плашками «???»
// (темы / расписание другой группы / ещё кое-что); одна приоткрывается на 0.4с
// и снова гаснет. Статично, лёгкий drift вверх. Музыка приседает (уже в треке).
// Переход fade.
export const S09: React.FC<SceneProps> = ({wide}) => {
	return (
		<SceneWrap bg={C.navy} in="fade">
			<MysteryCards />
			<div
				style={{
					position: 'absolute',
					left: 96,
					right: 96,
					...(wide
						? {bottom: 110, display: 'flex', justifyContent: 'center'}
						: {bottom: 240, display: 'flex', justifyContent: 'center'}),
				}}
			>
				<Title text="Ещё кое-что есть. Пока не скажем." accent="Пока не скажем" accentBg="#082647" delay={10} />
			</div>
		</SceneWrap>
	);
};
