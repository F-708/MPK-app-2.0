// v3.1 style bible — promo/VIDEO_V3_FINAL.md (single source for v3 components).
import {loadFont as loadGolos} from '@remotion/google-fonts/GolosText';
import {loadFont as loadMono} from '@remotion/google-fonts/JetBrainsMono';

export const C3 = {
	navy: '#0B3564',
	blue: '#0072CE',
	sub: '#5A6B80',
	bg: '#F7F9FB',
	radial: '#E8F0FE',
	cool: 'rgba(11,53,100,0.045)',
	hairline: 'rgba(11,53,100,0.10)',
	hi: 'rgba(255,255,255,0.7)',
	red: '#DC2626',
	snack: '#001737',
	white: '#FFFFFF',
};

export const Golos3 = loadGolos('normal', {
	weights: ['500', '700', '900'],
	subsets: ['cyrillic', 'latin'],
}).fontFamily;

export const Mono3 = loadMono('normal', {
	weights: ['600', '700'],
	subsets: ['cyrillic', 'latin'],
}).fontFamily;

export const W = 1080;
export const H = 1920;
// Шоты приходят 824×1830, работник перезапишет в 4x — пропорция та же, все кропы в долях.
export const SHOT_W = 824;
export const SHOT_H = 1830;
export const CARD_W = 912; // ≥760 (≥70% кадра)
export const CARD_TOP = 500;
export const RADIUS = CARD_W * 0.05; // radius = 5% ширины
export const TITLE_IN = 20; // квант 20f-сетки (90 BPM, бит=20f)

// Тени: свет сверху-слева → вниз; стек из спека + inset highlight + hairline.
export const CARD_SHADOW =
	'0 2px 6px rgba(11,53,100,0.08), 0 18px 36px rgba(11,53,100,0.10), 0 48px 96px rgba(11,53,100,0.14), inset 0 1px 0 ' +
	C3.hi;
export const CHIP_SHADOW =
	'0 2px 6px rgba(11,53,100,0.08), 0 18px 36px rgba(11,53,100,0.10), 0 48px 96px rgba(11,53,100,0.14)';
