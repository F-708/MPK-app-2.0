// finalF — стилевая библия PromoFinal9x16_d4 (1080×1920, 30fps, 900f). Self-contained.
import {loadFont} from '@remotion/google-fonts/Roboto';

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

export const F = loadFont('normal', {
	weights: ['400', '500', '700', '900'],
	subsets: ['cyrillic', 'latin'],
}).fontFamily;

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

export const BOUNDS = [0, 105, 270, 420, 585, 765, 900];
export const R2 = 2; // радиус UI-карточек — строго 2px (RoundedCornerShape(2.dp))
export const BG = `radial-gradient(1200px 1200px at 50% 32%, ${CF.brand} 0%, ${CF.navy} 72%)`;
export const DEPTH_SHADOW = '0 25px 50px -12px rgba(0, 0, 0, 0.35)';
