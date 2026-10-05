// AutoScroll3 — микро-событие S4: автоскролл текста translate y −12% за 50f easeInOut.
// Возвращает style для content-обёртки Card3 (штриха нет).
import React from 'react';
import {easeInOut} from './anim3';

export const AutoScroll3 = (frame: number): React.CSSProperties => ({
	transform: `translateY(${-12 * easeInOut((frame - 40) / 50)}%)`,
});
