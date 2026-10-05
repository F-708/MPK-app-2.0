// Общие тайминг-хелперы v3 (ease + spring-пресеты спека).
import {spring} from 'remotion';

export const clamp01 = (t: number) => Math.min(1, Math.max(0, t));
export const easeInOut = (t: number) => {
	const x = clamp01(t);
	return x * x * (3 - 2 * x);
};
// spring ζ0.9 15f — входы карточек/-push; ζ0.6 — pop чипа.
// Remotion SpringConfig живёт в damping: ζ = damping/(2√(mass·stiffness)), mass=1 stiffness=100.
export const spr = (frame: number, fps: number, zeta: number, dur: number, from = 0) =>
	frame <= from
		? 0
		: spring({
				frame: frame - from,
				fps,
				config: {damping: zeta * 20},
				durationInFrames: dur,
			});
