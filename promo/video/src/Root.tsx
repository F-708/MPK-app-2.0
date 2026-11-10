import {Composition, staticFile} from 'remotion';
import {Video} from './Video';
import {Video15} from './v2/Video15';
import {Video30} from './v3/Video30';
import {Video15v3} from './v3/Video15v3';
import {VideoFinal} from './final/VideoFinal';
import {VideoFinalF} from './finalF/VideoFinalF';
import {VideoA1} from './promo_a1/VideoA1';
import {VideoB2} from './promo_b2/VideoB2';
import {VideoC3} from './promo_c3/VideoC3';
import {VideoD4} from './promo_d4/VideoD4';
import {VideoE5} from './promo_e5/VideoE5';
import {VideoF6} from './promo_f6/VideoF6';
import {VideoG7} from './promo_g7/VideoG7';
import {VideoH8} from './promo_h8/VideoH8';

// Манифест наличия кадров (public/shots.json) пишет scripts/sync-shots.mjs.
// fetch(staticFile()) — официальный способ Remotion, работает и в Studio, и в рендере.
const calculateMetadata = async ({abortSignal}: {abortSignal: AbortSignal}) => {
	try {
		const res = await fetch(staticFile('shots.json'), {signal: abortSignal});
		const have: Record<string, string> = await res.json();
		return {props: {have}};
	} catch {
		return {props: {have: {} as Record<string, string>}};
	}
};

export const RemotionRoot: React.FC = () => {
	return (
		<>
			<Composition
				id="Promo9x16"
				component={Video}
				width={1080}
				height={1920}
				fps={30}
				durationInFrames={900}
				defaultProps={{have: {} as Record<string, string>}}
				calculateMetadata={calculateMetadata}
			/>
			<Composition
				id="Promo16x9"
				component={Video}
				width={1920}
				height={1080}
				fps={30}
				durationInFrames={900}
				defaultProps={{have: {} as Record<string, string>}}
				calculateMetadata={calculateMetadata}
			/>
			{/* v2: 15s cut — 450 кадров, только 9:16. */}
			<Composition id="Promo15" component={Video15} width={1080} height={1920} fps={30} durationInFrames={450} />
			{/* v3 FINAL: 30s master + 15s cut-down по VIDEO_V3_FINAL.md */}
			<Composition id="Promo30" component={Video30} width={1080} height={1920} fps={30} durationInFrames={900} />
			<Composition id="Promo15v3" component={Video15v3} width={1080} height={1920} fps={30} durationInFrames={450} />
			{/* FINAL: 30s master 9:16 по финальному брифу (гибрид PhoneMockup + камера) */}
			<Composition id="PromoFinal9x16" component={VideoFinal} width={1080} height={1920} fps={30} durationInFrames={900} />
			{/* finalF line (Task 2): 30s master 9:16 по PLAN_FINAL9x16.md */}
			<Composition id="PromoFinal9x16-d4" component={VideoFinalF} width={1080} height={1920} fps={30} durationInFrames={900} />
			{/* c3: 30s — «Задания» и «Оценки» (изолированная линия, promo_c3/) */}
			<Composition id="PromoC3" component={VideoC3} width={1080} height={1920} fps={30} durationInFrames={900} />
			{/* b2: 30s — «Карта колледжа» (изолированная линия, promo_b2/) */}
			<Composition id="PromoB2" component={VideoB2} width={1080} height={1920} fps={30} durationInFrames={900} />
			{/* a1: 30s — основная функция «Расписание» (изолированная линия, promo_a1/) */}
			<Composition id="PromoA1" component={VideoA1} width={1080} height={1920} fps={30} durationInFrames={900} />
			{/* d4: 30s — «Виджеты и звонки» (изолированная линия, promo_d4/) */}
			<Composition id="PromoD4" component={VideoD4} width={1080} height={1920} fps={30} durationInFrames={900} />
			{/* e5: 30s — функция «Преподаватели» (изолированная линия, promo_e5/) */}
			<Composition id="PromoE5" component={VideoE5} width={1080} height={1920} fps={30} durationInFrames={900} />
			{/* g7: 30s — функция «Материалы» (изолированная линия, promo_g7/) */}
			<Composition id="PromoG7" component={VideoG7} width={1080} height={1920} fps={30} durationInFrames={900} />
			{/* f6: 30s — функция «Оформление» (изолированная линия, promo_f6/) */}
			<Composition id="PromoF6" component={VideoF6} width={1080} height={1920} fps={30} durationInFrames={900} />
			{/* h8: 30s — общий обзор «всё в одном» (изолированная линия, promo_h8/) */}
			<Composition id="PromoH8" component={VideoH8} width={1080} height={1920} fps={30} durationInFrames={900} />
		</>
	);
};
