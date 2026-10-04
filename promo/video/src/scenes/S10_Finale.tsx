import React from 'react';
import {C, SceneProps} from '../theme';
import {SceneWrap} from '../components/Layout';
import {Finale} from '../components/Finale';

// A10 (840–900): финал #0B3564 — иконка + вордмарк «Мой Политех — весь колледж
// в одном приложении», сатира мелким 24sp #5B6B7A + «Релиз скоро».
// CTA-плашки НЕТ. Fade 300мс в конце.
export const S10: React.FC<SceneProps> = ({have}) => {
	return (
		<SceneWrap bg={C.navy} in="fade" out="fade">
			<Finale have={have} />
		</SceneWrap>
	);
};
