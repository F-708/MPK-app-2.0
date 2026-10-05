import React from 'react';
import {AbsoluteFill} from 'remotion';
import {BellDot} from '../../components/BellDot';
import {KineticTitle} from '../KineticTitle';
import {PhoneDark, ScreenImg, shotPt} from '../PhoneDark';
import {SecondsCard} from '../SecondsCard';

// S4 (162–234) Звонки: shot_bells contain; ПИКСЕЛЬ-ЯКОРЬ — бегущая точка по рельсу
// x=795/824, y 511→610 (linear за 72 кадра) + ореол-пульс 900мс Reverse (BellDot из v1,
// как в приложении). Точка живёт ВНУТРИ экрана (832×1664) и масштабируется с телефоном.
// contain-маппинг: shotPt(ix,iy) = {ox + ix/824*749.2, iy/1830*1664}, ox≈41.4.
// SecondsCard справа от телефона: mono 96px «4:32»→«4:29», янтарная рамка, pop на бите 180.
const FROM = shotPt(795, 511); // {left: 764.2, top: 464.7}
const TO = shotPt(795, 610); // {left: 764.2, top: 554.7}

export const S15_4: React.FC = () => {
	return (
		<AbsoluteFill>
			<PhoneDark scale={0.52} left={110} top={640}>
				<ScreenImg name="shot_bells" />
				<BellDot from={[FROM.left, FROM.top]} to={[TO.left, TO.top]} />
			</PhoneDark>
			<SecondsCard left={620} top={860} popAt={18} startSec={272} tickEvery={12} />
			<KineticTitle title="До звонка" accent="до секунды" titleAt={0} accentAt={18} left={96} top={240} width={540} />
		</AbsoluteFill>
	);
};
