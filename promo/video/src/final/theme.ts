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

// ---- Task 1 (final 9x16 foundation) ----

export const CF = {
	navy: '#001737', // глубина фона intro/outro
	brand: '#0B3564', // центр радиального градиента
	accent: '#0072CE',
	surface: '#F8FAFC',
	border: '#E2E8F0',
	title: '#111827',
	body: '#1F2937',
	muted: '#6B7280',
	badgeLab: '#8B5CF6',
	badgeExam: '#E11D48',
};

// Тот же loadFont('normal', {weights:['400','500','700','900'], subsets:['cyrillic','latin']}).
export const F = Roboto;

// Экран PhoneMockup(860, aspect 2) = 832×1664, стоит в (110,70) кадра.
export const SCR_W = 832;
export const SCR_H = 1664;
// Шоты 1648×3660; слой шота тянется по ширине экрана, вертикаль кропится поровну.
export const SHOT_W = 1648;
export const SHOT_H = 3660;
export const K = SCR_W / SHOT_W;
export const LAYER_H = SHOT_H * K;
export const CROP = (LAYER_H - SCR_H) / 2;
export const CX = SCR_W / 2;
export const CY = SCR_H / 2;

// BOUNDS [0,105,270,420,585,765,900] уже экспортируется выше (copy.bounds — те же значения).
export const R2 = 2; // радиус UI-карточек — строго 2px (RoundedCornerShape(2.dp))
export const BG = `radial-gradient(1200px 1200px at 50% 32%, ${CF.brand} 0%, ${CF.navy} 72%)`;
export const DEPTH_SHADOW = '0 25px 50px -12px rgba(0, 0, 0, 0.35)';
