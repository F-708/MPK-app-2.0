import {Composition, staticFile} from 'remotion';
import {Video} from './Video';
import {Video15} from './v2/Video15';
import {Video30} from './v3/Video30';
import {Video15v3} from './v3/Video15v3';
import {VideoFinal} from './final/VideoFinal';

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
		</>
	);
};
