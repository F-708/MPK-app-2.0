import {Audio, Series, staticFile, useVideoConfig} from 'remotion';
import {S01} from './scenes/S01_Hook';
import {S02} from './scenes/S02_Map';
import {S03} from './scenes/S03_LessonsWidget';
import {S04} from './scenes/S04_Bells';
import {S05} from './scenes/S05_Teachers';
import {S06} from './scenes/S06_Grades';
import {S07} from './scenes/S07_TaskPush';
import {S08} from './scenes/S08_Materials';
import {S09} from './scenes/S09_Mystery';
import {S10} from './scenes/S10_Finale';

// Тайминги варианта А (v0.4), SCRIPT_LOCKED_v1.0.md п.2: A1..A10, сумма 900.
export const Video: React.FC<{have: Record<string, string>}> = ({have}) => {
	const {width, height, fps} = useVideoConfig();
	const wide = width > height;
	const p = {have, wide};

	return (
		<>
			<Series>
				<Series.Sequence name="A1 hook" durationInFrames={60} premountFor={fps}>
					<S01 {...p} />
				</Series.Sequence>
				<Series.Sequence name="A2 map" durationInFrames={120} premountFor={fps}>
					<S02 {...p} />
				</Series.Sequence>
				<Series.Sequence name="A3 lessons+widget" durationInFrames={120} premountFor={fps}>
					<S03 {...p} />
				</Series.Sequence>
				<Series.Sequence name="A4 bells x3" durationInFrames={120} premountFor={fps}>
					<S04 {...p} />
				</Series.Sequence>
				<Series.Sequence name="A5 teachers" durationInFrames={90} premountFor={fps}>
					<S05 {...p} />
				</Series.Sequence>
				<Series.Sequence name="A6 grades" durationInFrames={90} premountFor={fps}>
					<S06 {...p} />
				</Series.Sequence>
				<Series.Sequence name="A7 task+push" durationInFrames={90} premountFor={fps}>
					<S07 {...p} />
				</Series.Sequence>
				<Series.Sequence name="A8 materials" durationInFrames={90} premountFor={fps}>
					<S08 {...p} />
				</Series.Sequence>
				<Series.Sequence name="A9 mystery" durationInFrames={60} premountFor={fps}>
					<S09 {...p} />
				</Series.Sequence>
				<Series.Sequence name="A10 finale" durationInFrames={60} premountFor={fps}>
					<S10 {...p} />
				</Series.Sequence>
			</Series>
			{/* В треке уже синтезированы: бит с 0:02, приседание 26–28с, аккорд 0:29. */}
			<Audio src={staticFile('audio/teaser_v1.wav')} />
		</>
	);
};
