import {Composition, staticFile} from 'remotion';
import {Video} from './Video';
import {Video15} from './v2/Video15';

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
		</>
	);
};
