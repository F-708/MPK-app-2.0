// Эмблема МГПК: пути 1:1 из ic_mpk_emblem.xml (viewBox 108), цвет — параметр.
import React from 'react';

const PATHS = [
	'M48,22 C48,20 60,20 60,22 L60,24 L48,24 Z',
	'M44,25 L64,25 L64,28 L44,28 Z',
	'M39,29 L69,29 L69,32 L39,32 Z',
	'M34,33 L74,33 L74,36 L34,36 Z',
	'M24,38 L84,38 L84,42 L24,42 Z',
	'M26,43 L82,43 L82,45 L26,45 Z',
	'M25,46 L31,46 L31,48 L25,48 Z M26,48 L30,48 L30,73 L26,73 Z M25,73 L31,73 L31,75 L25,75 Z',
	'M35,46 L41,46 L41,48 L35,48 Z M36,48 L40,48 L40,73 L36,73 Z M35,73 L41,73 L41,75 L35,75 Z',
	'M45,46 L51,46 L51,48 L45,48 Z M46,48 L50,48 L50,73 L46,73 Z M45,73 L51,73 L51,75 L45,75 Z',
	'M57,46 L63,46 L63,48 L57,48 Z M58,48 L62,48 L62,73 L58,73 Z M57,73 L63,73 L63,75 L57,75 Z',
	'M67,46 L73,46 L73,48 L67,48 Z M68,48 L72,48 L72,73 L68,73 Z M67,73 L73,73 L73,75 L67,75 Z',
	'M77,46 L83,46 L83,48 L77,48 Z M78,48 L82,48 L82,73 L78,73 Z M77,73 L83,73 L83,75 L77,75 Z',
	'M23,76 L85,76 L85,79 L23,79 Z',
	'M20,80 L88,80 L88,83 L20,83 Z',
	'M17,84 L91,84 L91,87 L17,87 Z',
];

export const Emblem: React.FC<{size: number; fill: string; style?: React.CSSProperties}> = ({
	size,
	fill,
	style,
}) => (
	<svg viewBox="0 0 108 108" width={size} height={size} style={style}>
		{PATHS.map((d, i) => (
			<path key={i} d={d} fill={fill} />
		))}
	</svg>
);
