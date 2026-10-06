// ZoomAvg3 — push-in зум 1.0→1.06 за 24f на B+35. Origin смещён влево/вверх от «4.2»
// (0.70,0.65 кропа): при 1.15 и ox=0.9029 левая кромка срезала буквы предметов
// (левый срез = ox*(s-1)*w). При 1.06/0.70 срез ~38px < отступа текста (~55px), «4.2» в кадре.
// Возвращает style для content-обёртки Card3.
import React from 'react';
import {easeInOut} from './anim3';

const OX = 0.7;
const OY = 0.65;

export const ZoomAvg3 = (frame: number, w: number, h: number): React.CSSProperties => ({
	transformOrigin: `${OX * w}px ${OY * h}px`,
	transform: `scale(${1 + 0.06 * easeInOut((frame - 35) / 24)})`,
});
