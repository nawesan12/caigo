import type { OgFija } from '#lib/og.ts';
import { faltanTexto } from '#lib/plan.ts';
import { h, img, type Estilo, type Nodo } from './motor.ts';
import { FOTOS, MARCA } from './recursos.ts';
import {
	avatares,
	C,
	chip,
	fondoClaro,
	GEL,
	grano,
	iconoCheck,
	iconoMas,
	marca,
	segmentos
} from './piezas.ts';

/*
 * Las OG fijas: portada (el splash 00 con tarjetas de "Ahora" en abanico), Plus (el paywall
 * violeta), lugares (la barra grafito del Panel Lugares) y una genérica para legales y demás.
 */

const LLENO: Estilo = { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0 };

function fondoOscuro(gradiente: string): Nodo[] {
	return [h('div', { ...LLENO, backgroundImage: gradiente }), grano(0.75)];
}

type Ejemplo = {
	foto: string;
	categoria: string;
	cuando: string;
	titulo: string;
	donde: string;
	faltan: number;
	somos: number;
	total: number;
	caras: string[];
};

/** Los planes de ejemplo del handoff (los mismos de la landing). */
const EJEMPLOS: Ejemplo[] = [
	{
		foto: FOTOS.previa,
		categoria: 'Previa',
		cuando: 'Hoy 23:00',
		titulo: 'Previa en La Perla',
		donde: 'La Perla · 2,4 km',
		faltan: 4,
		somos: 5,
		total: 9,
		caras: ['G', 'F', 'R']
	},
	{
		foto: FOTOS.truco,
		categoria: 'Juego',
		cuando: 'Hoy 21:30',
		titulo: 'Truco en un bar de Güemes',
		donde: 'Bar La Cumbre · 800 m',
		faltan: 1,
		somos: 3,
		total: 4,
		caras: ['C', 'L', 'N']
	},
	{
		foto: FOTOS.playa,
		categoria: 'Deporte',
		cuando: 'Hoy 20:30',
		titulo: 'Fútbol 5 en Punta Mogotes',
		donde: 'Cancha Los Pinos · 1,2 km',
		faltan: 2,
		somos: 8,
		total: 10,
		caras: ['M', 'J', 'L']
	}
];

/** La tarjeta de plan de "Ahora · lista" (02), en 340 px de ancho. */
function tarjeta(e: Ejemplo, extra: Estilo = {}, accion: string | null = 'Sumarme'): Nodo {
	const vidrio: Estilo = {
		padding: '7px 11px',
		borderRadius: 12,
		fontSize: 15,
		fontWeight: 700,
		lineHeight: 1
	};
	return h(
		'div',
		{
			position: 'absolute',
			width: 372,
			flexDirection: 'column',
			borderRadius: 28,
			overflow: 'hidden',
			backgroundColor: '#FFFFFF',
			boxShadow: 'inset 0 1px 0 #FFFFFF, 0 4px 0 #CFC8E6',
			...extra
		},
		h(
			'div',
			{ position: 'relative', width: 372, height: 184 },
			img(e.foto, 372, 184, { objectFit: 'cover', width: 372, height: 184 }),
			h('div', {
				...LLENO,
				backgroundImage: 'linear-gradient(180deg, rgba(20,18,28,.25) 0%, rgba(20,18,28,0) 45%)'
			}),
			h(
				'div',
				{ position: 'absolute', top: 14, left: 14, gap: 6 },
				h(
					'div',
					{ ...vidrio, backgroundColor: 'rgba(250,249,246,.92)', color: C.carbon },
					e.categoria
				),
				h('div', { ...vidrio, backgroundColor: 'rgba(32,29,43,.72)', color: C.calido }, e.cuando)
			),
			h(
				'div',
				{ position: 'absolute', right: 14, bottom: 14 },
				chip(faltanTexto(e.faltan), 'coral', 20)
			)
		),
		h(
			'div',
			{ flexDirection: 'column', gap: 14, padding: '16px 18px 20px' },
			h(
				'div',
				{ flexDirection: 'column', gap: 5 },
				h(
					'div',
					{ fontSize: 23, fontWeight: 700, letterSpacing: '-0.4px', color: C.carbon },
					e.titulo
				),
				h('div', { fontSize: 15, fontWeight: 600, color: C.gris }, e.donde)
			),
			h(
				'div',
				{ justifyContent: 'space-between', alignItems: 'center' },
				h(
					'div',
					{ alignItems: 'center', gap: 10 },
					avatares(
						e.caras.map((n) => ({ nombre: n })),
						e.somos,
						30,
						'#FFFFFF'
					),
					h(
						'div',
						{ fontSize: 14, fontWeight: 700, color: C.gris, whiteSpace: 'nowrap' },
						`Somos ${e.somos} de ${e.total}`
					)
				),
				accion &&
					h(
						'div',
						{
							alignItems: 'center',
							gap: 6,
							padding: '10px 15px',
							borderRadius: 14,
							fontSize: 16,
							fontWeight: 800,
							whiteSpace: 'nowrap',
							...GEL.lima,
							boxShadow:
								'inset 0 2px 1px rgba(255,255,255,.85), inset 0 -3px 0 rgba(96,140,10,.32), 0 3px 0 #94C43A'
						},
						accion === 'Sumarme' && iconoMas(13),
						accion
					)
			)
		)
	);
}

/** Portada: el splash grafito con la C, el lema y tres planes en abanico. */
function inicio(): Nodo {
	return h(
		'div',
		{ position: 'relative', width: 1200, height: 630, fontFamily: 'Geist', overflow: 'hidden' },
		...fondoOscuro(
			'radial-gradient(70% 80% at 85% 30%, #3B2F78 0%, rgba(59,47,120,0) 70%), radial-gradient(50% 60% at 0% 100%, #3A2533 0%, rgba(58,37,51,0) 70%), linear-gradient(#201D2B, #201D2B)'
		),
		// Abanico de tarjetas (la de atrás primero).
		tarjeta(EJEMPLOS[2], { left: 846, top: 150, transform: 'rotate(9deg)' }, null),
		tarjeta(EJEMPLOS[0], { left: 540, top: 182, transform: 'rotate(-8deg)' }, null),
		tarjeta(EJEMPLOS[1], { left: 688, top: 124, transform: 'rotate(1.5deg)' }),
		h(
			'div',
			{
				position: 'absolute',
				left: 72,
				top: 64,
				bottom: 64,
				width: 560,
				flexDirection: 'column'
			},
			img(MARCA.caigoIcono, 128, 128, { marginLeft: -8 }),
			h(
				'div',
				{
					marginTop: 18,
					fontSize: 120,
					fontWeight: 700,
					letterSpacing: '-0.05em',
					lineHeight: 0.95,
					color: C.calido
				},
				'Caigo'
			),
			h(
				'div',
				{ flexDirection: 'column', marginTop: 22, fontSize: 50, fontWeight: 800, lineHeight: 1.04 },
				h('div', { color: C.calido, letterSpacing: '-1.6px' }, 'Hacé planes.'),
				h('div', { color: C.lima, letterSpacing: '-1.6px' }, 'Conocé gente.')
			),
			h('div', { flexGrow: 1 }),
			h('div', { gap: 10 }, chip('Mar del Plata', 'oscuro', 21), chip('Villa Gesell', 'oscuro', 21))
		)
	);
}

/** Plus: el paywall (radial violeta sobre grafito), la C con el sello PLUS y la tabla. */
function plus(): Nodo {
	const filas: [string, string, string][] = [
		['Sumadas en Gente', '3 por día', 'Sin límite'],
		['Ver quién te sumó', '—', '✓'],
		['Tu plan destacado', '—', '1 por semana'],
		['Filtros finos', 'Básicos', 'Todos']
	];
	return h(
		'div',
		{ position: 'relative', width: 1200, height: 630, fontFamily: 'Geist', overflow: 'hidden' },
		...fondoOscuro(
			'radial-gradient(60% 75% at 22% 20%, #4A38A0 0%, rgba(74,56,160,0) 70%), radial-gradient(55% 70% at 100% 100%, #3B2F78 0%, rgba(59,47,120,0) 70%), linear-gradient(#201D2B, #201D2B)'
		),
		h(
			'div',
			{ position: 'absolute', left: 72, top: 60, width: 520, flexDirection: 'column' },
			h(
				'div',
				{ position: 'relative', width: 230, height: 190 },
				img(MARCA.caigoIcono, 176, 176),
				chip('PLUS', 'lima', 26, {
					position: 'absolute',
					left: 140,
					top: 6,
					fontWeight: 800,
					transform: 'rotate(10deg)'
				})
			),
			h(
				'div',
				{
					flexDirection: 'column',
					marginTop: 26,
					fontSize: 66,
					fontWeight: 800,
					lineHeight: 1.02,
					letterSpacing: '-2.2px',
					color: C.calido
				},
				h('div', {}, 'Más planes.'),
				h('div', {}, 'Más gente.'),
				h('div', { color: C.lima }, 'Sin límites.')
			),
			h(
				'div',
				{ marginTop: 22, fontSize: 24, fontWeight: 600, color: '#C9C4DA', lineHeight: 1.35 },
				'Sumá a quien quieras y destacá tus planes.'
			)
		),
		// La tabla del paywall, como tarjeta de vidrio con la columna PLUS violeta.
		h(
			'div',
			{
				position: 'absolute',
				right: 56,
				top: 96,
				width: 560,
				flexDirection: 'column',
				padding: '24px 26px',
				borderRadius: 32,
				backgroundColor: 'rgba(255,255,255,.07)',
				border: '1.5px solid rgba(255,255,255,.12)',
				transform: 'rotate(2deg)'
			},
			h('div', {
				position: 'absolute',
				right: 18,
				top: 14,
				bottom: 14,
				width: 150,
				borderRadius: 22,
				backgroundImage: 'linear-gradient(180deg, rgba(116,86,241,.55), rgba(116,86,241,.22))'
			}),
			h(
				'div',
				{ padding: '4px 0 14px', fontSize: 16, fontWeight: 800, letterSpacing: '1.5px' },
				h('div', { flexGrow: 1 }),
				h('div', { width: 120, justifyContent: 'center', color: '#A8A3BD' }, 'GRATIS'),
				h('div', { width: 150, justifyContent: 'center', color: C.lima }, 'PLUS')
			),
			...filas.map(([l, g, p], i) =>
				h(
					'div',
					{
						alignItems: 'center',
						padding: '17px 0',
						borderTop: i ? '1px solid rgba(255,255,255,.10)' : 'none'
					},
					h(
						'div',
						{ flexGrow: 1, fontSize: 20, fontWeight: 700, color: C.calido, whiteSpace: 'nowrap' },
						l
					),
					h(
						'div',
						{
							width: 120,
							justifyContent: 'center',
							fontSize: 17,
							fontWeight: 600,
							color: '#A8A3BD'
						},
						g
					),
					h(
						'div',
						{
							width: 150,
							justifyContent: 'center',
							fontSize: 18,
							fontWeight: 800,
							color: C.calido
						},
						p === '✓'
							? h(
									'div',
									{
										padding: '5px 8px',
										borderRadius: 11,
										...GEL.lima,
										boxShadow: '0 3px 0 #94C43A'
									},
									iconoCheck(20)
								)
							: p
					)
				)
			)
		),
		h(
			'div',
			{ position: 'absolute', right: 120, bottom: 40 },
			chip('Pasate a Plus', 'violeta', 28, {
				padding: '18px 30px',
				borderRadius: 22,
				fontWeight: 800
			})
		)
	);
}

/** Lugares: la barra grafito del panel a la izquierda y el preview "así lo ve la gente". */
function lugares(): Nodo {
	const stats: [string, string][] = [
		['Confirmados', '14 de 20'],
		['Cobrado', '$168.000'],
		['Check-ins', '9']
	];
	return h(
		'div',
		{
			position: 'relative',
			width: 1200,
			height: 630,
			fontFamily: 'Geist',
			overflow: 'hidden',
			backgroundColor: '#F3F0E9'
		},
		h('div', {
			...LLENO,
			backgroundImage:
				'radial-gradient(50% 60% at 100% 0%, #E3D9FF 0%, rgba(227,217,255,0) 70%), radial-gradient(40% 50% at 60% 100%, #FFE9DF 0%, rgba(255,233,223,0) 70%)'
		}),
		grano(0.9),
		// La barra lateral del panel.
		h(
			'div',
			{
				position: 'absolute',
				left: 0,
				top: 0,
				bottom: 0,
				width: 600,
				flexDirection: 'column',
				padding: '58px 64px 60px',
				borderRadius: '0 48px 48px 0',
				overflow: 'hidden'
			},
			...fondoOscuro(
				'radial-gradient(120% 60% at 0% 0%, #3B2F78 0%, rgba(59,47,120,0) 70%), linear-gradient(#201D2B, #201D2B)'
			),
			h(
				'div',
				{ alignItems: 'center', gap: 16 },
				marca(50, C.calido),
				h(
					'div',
					{
						marginTop: 6,
						fontSize: 17,
						fontWeight: 800,
						letterSpacing: '2px',
						color: C.lima
					},
					'PARA LUGARES'
				)
			),
			h(
				'div',
				{
					flexDirection: 'column',
					marginTop: 54,
					fontSize: 62,
					fontWeight: 800,
					lineHeight: 1.03,
					letterSpacing: '-2px',
					color: C.calido
				},
				h('div', {}, 'Publicá un plan.'),
				h('div', { color: C.lima }, 'Llená tu lugar.')
			),
			h(
				'div',
				{ marginTop: 22, fontSize: 23, fontWeight: 600, color: '#C9C4DA', lineHeight: 1.4 },
				'Entradas y señas con Mercado Pago, check-in con Caigo Tap. Publicar es gratis.'
			),
			h('div', { flexGrow: 1 }),
			h(
				'div',
				{},
				chip([iconoMas(20), 'Publicar plan'], 'lima', 24, {
					gap: 10,
					padding: '16px 24px',
					borderRadius: 18,
					fontWeight: 800,
					boxShadow:
						'inset 0 3px 2px rgba(255,255,255,.85), inset 0 -4px 0 rgba(96,140,10,.3), 0 4px 0 #7FA82E'
				})
			)
		),
		// Tarjeta neutra con los números del plan (detrás) y el preview del plan (adelante).
		h(
			'div',
			{
				position: 'absolute',
				left: 680,
				top: 58,
				width: 450,
				flexDirection: 'column',
				gap: 14,
				padding: '22px 26px 26px',
				borderRadius: 28,
				backgroundColor: '#FFFFFF',
				boxShadow: 'inset 0 1px 0 #FFFFFF, 0 4px 0 #E6E0F3',
				transform: 'rotate(4deg)'
			},
			h(
				'div',
				{ fontSize: 14, fontWeight: 800, letterSpacing: '1px', color: C.gris },
				'TALLER DE CERÁMICA · SÁB 18:00'
			),
			h(
				'div',
				{ gap: 10 },
				...stats.map(([l, v]) =>
					h(
						'div',
						{
							flexDirection: 'column',
							gap: 4,
							flexGrow: 1,
							whiteSpace: 'nowrap',
							padding: '12px 14px',
							borderRadius: 18,
							backgroundColor: C.lavanda
						},
						h('div', { fontSize: 13, fontWeight: 700, color: C.gris }, l),
						h('div', { fontSize: 22, fontWeight: 800, letterSpacing: '-0.5px', color: C.carbon }, v)
					)
				)
			),
			segmentos(14, 20, 10, 4)
		),
		tarjeta(
			{
				foto: FOTOS.lugar,
				categoria: 'Taller',
				cuando: 'Sáb 18:00',
				titulo: 'Taller de cerámica',
				donde: 'Café Molienda · Güemes',
				faltan: 6,
				somos: 14,
				total: 20,
				caras: ['M', 'A', 'S']
			},
			{ left: 730, top: 236, transform: 'rotate(-4deg)' },
			'Entrada'
		),
		h(
			'div',
			{ position: 'absolute', left: 1000, top: 262, transform: 'rotate(8deg)' },
			chip('$12.000', 'violeta', 22, { fontWeight: 800 })
		)
	);
}

/** Genérica (legales, arrepentimiento, errores): la marca grande sobre el fondo cálido. */
function caigo(): Nodo {
	return h(
		'div',
		{
			position: 'relative',
			width: 1200,
			height: 630,
			fontFamily: 'Geist',
			overflow: 'hidden',
			backgroundColor: C.calido
		},
		...fondoClaro(),
		img(MARCA.caigoC, 530, 438, {
			position: 'absolute',
			right: 60,
			top: 96,
			transform: 'rotate(-6deg)'
		}),
		h(
			'div',
			{
				position: 'absolute',
				left: 80,
				top: 0,
				bottom: 0,
				flexDirection: 'column',
				justifyContent: 'center'
			},
			h(
				'div',
				{
					fontSize: 150,
					fontWeight: 700,
					letterSpacing: '-0.05em',
					lineHeight: 0.95,
					color: C.carbon
				},
				'Caigo'
			),
			h(
				'div',
				{ flexDirection: 'column', marginTop: 26, fontSize: 52, fontWeight: 800, lineHeight: 1.05 },
				h('div', { letterSpacing: '-1.6px', color: C.carbon }, 'Hacé planes.'),
				h('div', { letterSpacing: '-1.6px', color: C.violeta }, 'Conocé gente.')
			),
			h(
				'div',
				{ gap: 10, marginTop: 36 },
				chip('Mar del Plata', 'apagado', 22),
				chip('Villa Gesell', 'apagado', 22)
			)
		)
	);
}

export const FIJAS: Record<OgFija, () => Nodo> = { inicio, plus, lugares, caigo };
