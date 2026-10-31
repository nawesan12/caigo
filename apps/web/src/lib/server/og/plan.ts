import { TINTES } from '#lib/app.ts';
import { partes } from '#lib/fechas.ts';
import { CATEGORIAS, faltanTexto, primerNombre } from '#lib/plan.ts';
import { desglose, pesos } from '#lib/precios.ts';
import type { Plan } from '#lib/tipos.ts';
import { h, img, type Nodo } from './motor.ts';
import { MARCA } from './recursos.ts';
import {
	avatar,
	avatares,
	C,
	chip,
	cortar,
	fondoClaro,
	grano,
	limpio,
	marca,
	segmentos,
	sinViudas,
	type Cara
} from './piezas.ts';

/*
 * La OG de un plan (/p/<slug>/og.png): el link público (pantalla 12) con el contador del detalle
 * (05) como protagonista. A la izquierda la "hoja" cálida con textura (marca, chips, título y quién
 * invita); a la derecha la foto del plan y, encima, la tarjeta grafito con "Faltan N" gigante,
 * "Somos X de Y", las caras y la barra de cupo.
 */

const DIAS = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

/** "Sáb 1 nov · 18:30": absoluta, porque la imagen se guarda en cachés y "Hoy" envejece mal. */
export function cuandoOg(f: string): string {
	const p = partes(f);
	return `${DIAS[p.semana]} ${p.dia} ${MESES[p.mes]} · ${p.hora}`;
}

/** Lo que muestra la imagen, ya resuelto (las fotos como data: URL). */
export type DatosOgPlan = {
	plan: Plan;
	foto: string | null;
	caras: Cara[];
	anfitrion: Cara & { esLugar: boolean };
};

function tamTitulo(t: string): number {
	if (t.length <= 20) return 70;
	if (t.length <= 30) return 62;
	if (t.length <= 48) return 56;
	return 50;
}

function precioDe(p: Plan): string | null {
	if (p.cobro === 'gratis' || !p.precioCentavos) return null;
	const d = desglose(p.cobro, p.precioCentavos / 100, p.senaPct ?? 0);
	return p.cobro === 'sena' ? `Seña ${pesos(d.paga)}` : `Entrada ${pesos(d.paga)}`;
}

function contador(p: Plan, caras: Cara[]): Nodo {
	const cancelado = p.cancelado;
	const completo = !cancelado && p.faltan <= 0;
	const grande = cancelado ? 'Se canceló' : faltanTexto(p.faltan);
	return h(
		'div',
		{
			position: 'absolute',
			right: 40,
			bottom: 46,
			width: 520,
			flexDirection: 'column',
			gap: 18,
			padding: '30px 34px 34px',
			borderRadius: 38,
			overflow: 'hidden',
			transform: 'rotate(-2deg)',
			backgroundImage: 'radial-gradient(120% 90% at 0% 0%, #3D3756 0%, #24212D 62%)',
			boxShadow: 'inset 0 1px 0 rgba(255,255,255,.10), 0 6px 0 #0E0D14'
		},
		grano(0.55),
		h(
			'div',
			{
				// "Completo" y los de dos cifras son más anchos: que entren en la tarjeta.
				fontSize: cancelado
					? 84
					: completo
						? 100
						: grande.length <= 8
							? 118
							: grande.length === 9
								? 104
								: 94,
				fontWeight: 800,
				lineHeight: 0.92,
				letterSpacing: cancelado ? '-3px' : '-6px',
				whiteSpace: 'nowrap',
				color: cancelado ? '#B9B4CC' : completo ? C.lima : C.coral
			},
			grande
		),
		h(
			'div',
			{ justifyContent: 'space-between', alignItems: 'center' },
			h(
				'div',
				{ fontSize: 30, fontWeight: 600, color: C.calido, letterSpacing: '-0.3px' },
				`Somos ${p.somos} de ${p.total}`
			),
			avatares(caras, p.somos, 56, '#2B2738')
		),
		segmentos(p.somos, p.total, 14, 7)
	);
}

function foto(datos: DatosOgPlan): Nodo {
	const { plan } = datos;
	const tinte = TINTES[plan.categoria] ?? '#5B43C9';
	const capa = h('div', {
		position: 'absolute',
		top: 0,
		left: 0,
		right: 0,
		bottom: 0,
		backgroundImage:
			'linear-gradient(180deg, rgba(20,18,28,0) 38%, rgba(20,18,28,.55) 100%), linear-gradient(90deg, rgba(20,18,28,.28) 0%, rgba(20,18,28,0) 24%)'
	});
	if (datos.foto)
		return h(
			'div',
			{ position: 'absolute', top: 0, right: 0, bottom: 0, width: 640, backgroundColor: tinte },
			img(datos.foto, 640, 630, { objectFit: 'cover', width: 640, height: 630 }),
			capa
		);
	// Sin foto: el color de la categoría con textura y la C 3D grande, como un póster.
	return h(
		'div',
		{
			position: 'absolute',
			top: 0,
			right: 0,
			bottom: 0,
			width: 640,
			overflow: 'hidden',
			backgroundImage: `radial-gradient(90% 80% at 70% 20%, rgba(255,255,255,.22) 0%, rgba(255,255,255,0) 60%), linear-gradient(160deg, ${tinte} 0%, #201D2B 100%)`
		},
		grano(0.7),
		// A tamaño natural (265×219): agrandada, el borde suave del PNG se ve serruchado.
		img(MARCA.caigoC, 265, 219, {
			position: 'absolute',
			top: 52,
			right: 150,
			transform: 'rotate(-8deg)'
		}),
		capa
	);
}

export function ogPlan(datos: DatosOgPlan): Nodo {
	const { plan, anfitrion } = datos;
	const titulo = sinViudas(cortar(limpio(plan.titulo), 64));
	const cat = CATEGORIAS[plan.categoria] ?? 'Plan';
	const donde = [
		plan.lugar?.nombre ? plan.lugar.nombre : plan.zona ? `Zona ${plan.zona}` : plan.lugarNombre,
		plan.ciudad
	]
		.filter(Boolean)
		.map((s) => limpio(String(s)))
		.join(', ');
	const precio = precioDe(plan);
	const quien = anfitrion.esLugar
		? limpio(anfitrion.nombre)
		: primerNombre(limpio(anfitrion.nombre)) || 'Alguien';

	return h(
		'div',
		{
			position: 'relative',
			width: 1200,
			height: 630,
			fontFamily: 'Geist',
			color: C.carbon,
			backgroundColor: C.grafito
		},
		foto(datos),
		precio &&
			h(
				'div',
				{ position: 'absolute', top: 40, right: 44 },
				chip(precio, plan.cobro === 'sena' ? 'violeta' : 'lima', 26)
			),
		// Sombras grandes pintadas con gradientes: un blur de resvg de este tamaño tarda segundos.
		h('div', {
			position: 'absolute',
			top: 0,
			bottom: 0,
			left: 560,
			width: 190,
			backgroundImage: 'linear-gradient(90deg, rgba(14,13,20,.55) 0%, rgba(14,13,20,0) 100%)'
		}),
		h('div', {
			position: 'absolute',
			right: -20,
			bottom: -60,
			width: 660,
			height: 300,
			backgroundImage: 'radial-gradient(closest-side, rgba(14,13,20,.7), rgba(14,13,20,0))'
		}),
		// La hoja cálida (como la del detalle, que sube sobre la foto), con borde redondeado.
		h(
			'div',
			{
				position: 'absolute',
				top: 0,
				left: 0,
				bottom: 0,
				width: 640,
				flexDirection: 'column',
				padding: '50px 60px 52px 64px',
				borderRadius: '0 56px 56px 0',
				overflow: 'hidden',
				backgroundColor: C.calido
			},
			...fondoClaro(),
			marca(46),
			h(
				'div',
				{ gap: 12, marginTop: 40, alignItems: 'center' },
				chip(cat, 'apagado', 23),
				chip(cuandoOg(plan.empiezaEn), plan.cancelado ? 'apagado' : 'coral', 23)
			),
			h(
				'div',
				{
					marginTop: 26,
					fontSize: tamTitulo(titulo),
					fontWeight: 700,
					lineHeight: 1.04,
					letterSpacing: '-0.035em',
					color: C.carbon
				},
				titulo
			),
			h('div', { flexGrow: 1 }),
			h(
				'div',
				{ alignItems: 'center', gap: 18 },
				avatar(anfitrion, 0, 64, '3px solid #FFFFFF', {
					borderRadius: anfitrion.esLugar ? 20 : 32,
					boxShadow: '0 3px 0 #E6E0F3, 0 10px 20px -8px rgba(80,60,160,.35)'
				}),
				h(
					'div',
					{ flexDirection: 'column', gap: 6, flexShrink: 1 },
					h(
						'div',
						{ fontSize: 28, fontWeight: 700, letterSpacing: '-0.4px' },
						anfitrion.esLugar ? `Organiza ${cortar(quien, 26)}` : `${cortar(quien, 18)} te invita`
					),
					h(
						'div',
						{ fontSize: 22, fontWeight: 600, color: C.gris },
						cortar(donde, 40) || 'Mar del Plata'
					)
				)
			)
		),
		contador(plan, datos.caras)
	);
}
