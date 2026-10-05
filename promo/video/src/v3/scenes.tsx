// Сцены v3 (S1…S7) — компоненты чистые по локальному кадру Sequence; cut-down переиспользует
// их же с props-смещением длительностей (copy.json cut).
import React from 'react';
import {useCurrentFrame, useVideoConfig} from 'remotion';
import {easeInOut} from './anim3';
import {AutoScroll3} from './AutoScroll3';
import {BellDot3} from './BellDot3';
import {Card3, cardHeight, type Crop} from './Card3';
import {ChevronOverlay3} from './ChevronOverlay3';
import copy from './copy.json';
import {Finale3} from './Finale3';
import {Push3} from './Push3';
import {SecondsChip3} from './SecondsChip3';
import {Stage3} from './Stage3';
import {SwipeRow3, SwipeUndo3} from './SwipeUndo3';
import {CARD_TOP, CARD_W} from './theme3';
import {Title3} from './Title3';
import {ZoomAvg3} from './ZoomAvg3';

type SP = {dur?: number};
const sc = (id: string) => copy.scenes.find((s) => s.id === id)!;
const useFC = () => ({frame: useCurrentFrame(), fps: useVideoConfig().fps});

export const V3S1: React.FC<SP> = ({dur = 140}) => {
	const {frame, fps} = useFC();
	const s = sc('S1');
	const crop = s.crop as Crop;
	const h = cardHeight(crop);
	return (
		<Stage3 digit={s.caption} digitSide={s.digitSide as 'left' | 'right'}>
			<Push3 dur={dur} enter="open">
				<Card3 shot={s.shot!} crop={crop} frame={frame} fps={fps} dur={dur} enter="open"
					// push-in 1.0→1.08 вдоль маршрута 60f easeInOut с кадра 15 (origin — верх ломаной)
					contentStyle={{
						transformOrigin: `${0.4988 * CARD_W}px ${0.3863 * h}px`,
						transform: `scale(${1 + 0.08 * easeInOut((frame - 15) / 60)})`,
					}}>
					<ChevronOverlay3 frame={frame} w={CARD_W} h={h} />
				</Card3>
				<Title3 lines={s.title!} sub={s.sub!} frame={frame} />
			</Push3>
		</Stage3>
	);
};

export const V3S2: React.FC<SP> = ({dur = 120}) => {
	const {frame, fps} = useFC();
	const s = sc('S2');
	return (
		<Stage3 digit={s.caption} digitSide={s.digitSide as 'left' | 'right'}>
			<Push3 dur={dur} enter="push">
				<Card3 shot={s.shot!} crop={s.crop as Crop} frame={frame} fps={fps} dur={dur} enter="push" />
				<Title3 lines={s.title!} sub={s.sub!} frame={frame} />
			</Push3>
		</Stage3>
	);
};

export const V3S3: React.FC<SP> = ({dur = 115}) => {
	const {frame, fps} = useFC();
	const s = sc('S3');
	const h = cardHeight(s.crop as Crop);
	return (
		<Stage3 digit={s.caption} digitSide={s.digitSide as 'left' | 'right'}>
			<Push3 dur={dur} enter="push">
				<Card3 shot={s.shot!} crop={s.crop as Crop} frame={frame} fps={fps} dur={dur} enter="push">
					<BellDot3 frame={frame} dur={dur} w={CARD_W} h={h} />
				</Card3>
				<Title3 lines={s.title!} sub={s.sub!} frame={frame} />
				<SecondsChip3 frame={frame} fps={fps} />
			</Push3>
		</Stage3>
	);
};

export const V3S4: React.FC<SP> = ({dur = 110}) => {
	const {frame, fps} = useFC();
	const s = sc('S4');
	return (
		<Stage3 digit={s.caption} digitSide={s.digitSide as 'left' | 'right'}>
			<Push3 dur={dur} enter="push">
				<Card3
					shot={s.shot!}
					crop={s.crop as Crop}
					frame={frame}
					fps={fps}
					dur={dur}
					enter="push"
					contentStyle={AutoScroll3(frame)}
				/>
				<Title3 lines={s.title!} sub={s.sub!} frame={frame} />
			</Push3>
		</Stage3>
	);
};

export const V3S5: React.FC<SP> = ({dur = 165}) => {
	const {frame, fps} = useFC();
	const s = sc('S5');
	return (
		<Stage3 digit={s.caption} digitSide={s.digitSide as 'left' | 'right'}>
			<Push3 dur={dur} enter="push">
				<Card3 shot={s.shot!} crop={s.crop as Crop} frame={frame} fps={fps} dur={dur} enter="push">
					<SwipeRow3 frame={frame} />
				</Card3>
				<Title3 lines={s.title!} sub={s.sub!} frame={frame} />
				<SwipeUndo3 frame={frame} top={CARD_TOP + cardHeight(s.crop as Crop) + 50} />
			</Push3>
		</Stage3>
	);
};

export const V3S6: React.FC<SP> = ({dur = 135}) => {
	const {frame, fps} = useFC();
	const s = sc('S6');
	const h = cardHeight(s.crop as Crop);
	return (
		<Stage3 digit={s.caption} digitSide={s.digitSide as 'left' | 'right'}>
			<Push3 dur={dur} enter="push">
				<Card3
					shot={s.shot!}
					crop={s.crop as Crop}
					frame={frame}
					fps={fps}
					dur={dur}
					enter="push"
					contentStyle={ZoomAvg3(frame, CARD_W, h)}
				/>
				<Title3 lines={s.title!} sub={s.sub!} frame={frame} />
			</Push3>
		</Stage3>
	);
};

export const V3S7: React.FC<SP & {pushInStart?: number}> = ({dur = 115, pushInStart = 30}) => {
	const {frame} = useFC();
	return (
		<Stage3 digit={null}>
			<Push3 dur={dur} enter="push" out={false}>
				<Finale3 frame={frame} pushInStart={pushInStart} />
			</Push3>
		</Stage3>
	);
};
