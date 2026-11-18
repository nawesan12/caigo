import { redirect } from '@sveltejs/kit';
import { api, ErrorApi, ipDe, planesQuizas } from '#lib/server/api.ts';
import { conUbicacion } from '#lib/server/app.ts';
import { CATEGORIAS } from '#lib/plan.ts';
import { rangoFecha, ZONAS_FIJAS } from '#lib/app.ts';
import type { Plan } from '#lib/tipos.ts';
import type { PageServerLoad } from './$types';

/** Las zonas de la ciudad (contrato §5); si la API todavía no las da, las fijas. */
async function zonasDe(event: Parameters<PageServerLoad>[0], ciudad: string): Promise<string[]> {
	try {
		const r = await api<{ zonas: string[] }>(
			'GET',
			`/v1/publico/zonas?ciudad=${encodeURIComponent(ciudad)}`,
			{ ip: ipDe(event) }
		);
		if (r?.zonas?.length) return r.zonas;
	} catch {
		/* respaldo */
	}
	return ZONAS_FIJAS[ciudad] ?? ZONAS_FIJAS['Mar del Plata'];
}

/**
 * Explorar (04): la agenda de las próximas dos semanas. Filtros: categoría (tiles), fecha (hoy,
 * mañana, el finde o un día), zona y búsqueda (básicos); «Sólo chicas» y «verificados» (seguridad,
 * gratis siempre); el cupo es de Plus (comodidad).
 */
export const load: PageServerLoad = async (event) => {
	const sp = event.url.searchParams;
	const c = sp.get('categoria') ?? '';
	// hasOwn y no `in`: "toString" o "__proto__" están en cualquier objeto y la API daba 400.
	const categoria = Object.hasOwn(CATEGORIAS, c) ? c : '';
	const verificados = sp.get('verificados') === '1';
	const chicas = sp.get('chicas') === '1';
	const f = sp.get('faltan') ?? '';
	const faltan = ['2', '3', '4'].includes(f) ? f : '';
	const fecha = sp.get('fecha') ?? '';
	const rango = rangoFecha(fecha);
	const busqueda = (sp.get('q') ?? '').trim().slice(0, 60);
	const q = busqueda.length >= 2 ? busqueda : '';
	const { yo } = await event.parent();
	const ciudad = yo?.ciudad || 'Mar del Plata';
	const zonas = await zonasDe(event, ciudad);
	const z = sp.get('zona') ?? '';
	const zona = zonas.includes(z) ? z : '';

	const qs = conUbicacion(
		event,
		{
			categoria,
			verificados: verificados ? '1' : '',
			chicas: chicas ? '1' : '',
			faltan,
			zona,
			q,
			desde: rango?.desde ?? '',
			hasta: rango?.hasta ?? ''
		},
		ciudad
	);
	let planes: Plan[];
	let aviso: string;
	try {
		({ planes, aviso } = await planesQuizas(
			event,
			`/v1/planes/explorar?${qs}`,
			`/v1/publico/planes/explorar?${qs}`
		));
	} catch (e) {
		// El cupo es un filtro fino: de Caigo Plus.
		if (e instanceof ErrorApi && e.codigo === 'plus') redirect(303, '/plus');
		throw e;
	}
	// Respaldo mientras la API no filtra por `q` y `zona` (contrato §5): mismo criterio.
	const n = (t: string) =>
		t
			.normalize('NFD')
			.replace(/[\u0300-\u036f]/g, '')
			.toLowerCase();
	if (q)
		planes = planes.filter((p) =>
			n([p.titulo, p.zona, p.lugarNombre, p.lugar?.nombre ?? ''].join(' ')).includes(n(q))
		);
	if (zona) planes = planes.filter((p) => p.zona === zona);
	return {
		planes,
		aviso,
		categoria,
		verificados,
		chicas,
		faltan,
		zona,
		zonas,
		q: busqueda,
		fecha: rango ? fecha : '',
		diasFecha: rango?.dias ?? [],
		dia: sp.get('dia') ?? ''
	};
};
