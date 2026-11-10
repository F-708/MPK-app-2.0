// Локальная тема PromoA1 — изолирована от src/theme.ts, ничего чужого не трогаем.
import {loadFont as loadGolos} from '@remotion/google-fonts/GolosText';

export const C = {
	navy: '#0B3564',
	blue: '#0067BB',
	bg: '#F4F6F8',
	card: '#FFFFFF',
	ink: '#111820',
	sub: '#5B6B7A',
	line: '#E2E8F0',
	hi: '#E8F0FA',
};

export const Golos = loadGolos('normal', {
	weights: ['400', '500', '700', '900'],
	subsets: ['cyrillic', 'latin'],
}).fontFamily;
