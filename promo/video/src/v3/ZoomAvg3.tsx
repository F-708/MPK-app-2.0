// ZoomAvg3 — push-in зум 1.0→1.15 за 24f на B+35, origin на «4.2» полного шота shot_grades
// (744,1081) → crop S6 y[0.30,0.72]: xF=744/824=0.9029, yF=(1081/1830−0.30)/0.42=0.6922.
// Возвращает style для content-обёртки Card3.
import React from 'react';
import {easeInOut} from './anim3';

const OX = 744 / 824;
const OY = (1081 / 1830 - 0.3) / 0.42;

export const ZoomAvg3 = (frame: number, w: number, h: number): React.CSSProperties => ({
	transformOrigin: `${OX * w}px ${OY * h}px`,
	transform: `scale(${1 + 0.15 * easeInOut((frame - 35) / 24)})`,
});
