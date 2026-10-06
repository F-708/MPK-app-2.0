// Дизайн-система «Мой Политех» — финальная композиция PromoFinal9x16 (бриф Фазы 2).
import {loadFont as loadRoboto} from '@remotion/google-fonts/Roboto';
import copy from './copy.json';

export const C = {
	navyDeep: '#001737', // Intro/Outro фон
	navy: '#0B3564',
	blue: '#0072CE',
	surface: '#F8FAFC',
	border: '#E2E8F0',
	title: '#111827',
	body: '#1F2937',
	muted: '#6B7280',
	white: '#FFFFFF',
};

export const Roboto = loadRoboto('normal', {
	weights: ['400', '500', '700', '900'],
	subsets: ['cyrillic', 'latin'],
}).fontFamily;

export const W = 1080;
export const H = 1920;
export const DUR = 900;
// Границы сцен: 0/105/270/420/585/765/900
export const BOUNDS = copy.bounds;
export const SCENE_DUR = BOUNDS.slice(1).map((b, i) => b - BOUNDS[i]);

// Моккап: экран 680×1510 (соотношение шота 1648×3660), телефон 700×1530.
export const PHONE = {x: 190, y: 350, w: 700, bez: 10};
export const SCR = {w: PHONE.w - 2 * PHONE.bez, h: (PHONE.w - 2 * PHONE.bez) * (3660 / 1648)};

// Фон сцен-фич: нейтральная светлая поверхность приложения.
export const BG_LIGHT = 'linear-gradient(180deg, #F8FAFC 0%, #EDF1F5 100%)';
