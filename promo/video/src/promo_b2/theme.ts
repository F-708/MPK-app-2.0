// promo_b2 — стиль для «Карта колледжа». Self-contained, свои шоты 360×780.
import {loadFont} from '@remotion/google-fonts/Roboto';

export const CB = {
	navy: '#0B3564',
	blue: '#0067BB',
	bg: '#F4F6F8',
	ink: '#111820',
	sub: '#5B6B7A',
	lawn: '#DCE8D2',
};

export const FB = loadFont('normal', {
	weights: ['400', '500', '700', '900'],
	subsets: ['cyrillic', 'latin'],
}).fontFamily;

// Экран PhoneMockup(860, aspect 2) = 832×1664; шот 360×780 вписываем по ширине,
// вертикаль кропим по центру (contain-by-width).
export const SCR_W = 832;
export const SCR_H = 1664;
export const SHOT_W = 360;
export const SHOT_H = 780;
export const KB = SCR_W / SHOT_W; // ≈2.311
export const LAYER_HB = SHOT_H * KB; // ≈1802.7
export const CROPB = (LAYER_HB - SCR_H) / 2;

export const BG_B = `radial-gradient(1200px 1200px at 50% 30%, ${CB.blue}22 0%, ${CB.navy} 78%)`;
