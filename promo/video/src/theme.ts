// Стилевая библия — promo/SCRIPT_LOCKED_v1.0.md п.4
import {loadFont as loadGolos} from '@remotion/google-fonts/GolosText';
import {loadFont as loadMono} from '@remotion/google-fonts/JetBrainsMono';

export const C = {
	navy: '#0B3564',
	blue: '#0072CE',
	amber: '#FFB020',
	bg: '#F4F6F8',
	ink: '#111820',
	sub: '#5B6B7A',
	widgetMain: '#111827', // WidgetStyle.LIGHT mainColor
	widgetSub: '#6B7280', // WidgetStyle.LIGHT subColor
	widgetHi: '#E8F0FA', // WidgetStyle.LIGHT highlightColor
	widgetDivider: '#E2E8F0',
};

export const Golos = loadGolos('normal', {
	weights: ['400', '500', '700', '900'],
	subsets: ['cyrillic', 'latin'],
}).fontFamily;

export const Mono = loadMono('normal', {
	weights: ['700'],
	subsets: ['cyrillic', 'latin'],
}).fontFamily;

// Android sp → px видео-кадра 1080 шириной.
// ponytail: калибровочная ручка — подкрутить, если реплики хрома крупнее/мельче устройства.
export const SP = 2.6;

export type SceneProps = {
	have: Record<string, string>;
	wide: boolean;
};

// Геометрия мокапа в 9:16 (для координат выносок-Callout в сценах).
export const MOCK_9 = {x: 110, y: 70, w: 860, h: 1692};
// Геометрия мокапа в 16:9: высота 920, правая треть.
export const MOCK_16 = {x: 1344, y: 80, w: 446, h: 920};
// 16:9: контент экрана опущен на высоту пилюли динамика + 8px, чтобы орг-шапка шота
// не пряталась под пилюль (vision-QA3 wide_f420). Учитывается в Layout и projectShot.
export const NOTCH = 36;
