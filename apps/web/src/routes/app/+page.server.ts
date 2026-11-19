import { conSesion, planesQuizas } from '#lib/server/api.ts';
import { semanaVisible } from '#lib/dia.ts';
import type { Semana } from '#lib/tipos.ts';
import { conUbicacion, planDeHoy } from '#lib/server/app.ts';
import { FILTROS_AHORA } from '#lib/app.ts';
import type { PageServerLoad } from './$types';

/** Ahora · lista (02): los planes de las próximas 3 horas cerca. */
export const load: PageServerLoad = async (event) => {
	const c = event.url.searchParams.get('categoria') ?? '';
	const categoria = FILTROS_AHORA.some((f) => f.valor === c) ? c : '';
	// «Sólo chicas» (planes «Solo mujeres»): seguridad, gratis para todos.
	const chicas = event.url.searchParams.get('chicas') === '1';
	const { yo } = await event.parent();
	// Planes · Gente · Citas (la cabecera de Ahora, como en las apps). Citas, sólo con esa intención.
	const m = event.url.searchParams.get('modo') ?? '';
	const modo =
		m === 'gente' || (m === 'citas' && yo?.intenciones?.includes('citas')) ? m : 'planes';
	if (modo !== 'planes')
		return { planes: [], aviso: '', categoria, chicas, hoy: null, semana: null, modo };
	const q = conUbicacion(event, { categoria, chicas: chicas ? '1' : '' }, yo?.ciudad);
	const { planes, aviso } = await planesQuizas(
		event,
		`/v1/planes/ahora?${q}`,
		`/v1/publico/planes/ahora?${q}`
	);
	// «Esta semana en <ciudad>»: la tarjeta de los lunes (hasta el miércoles), si hay 5 planes o más.
	let semana: Semana | null = null;
	if (event.locals.token && yo?.ciudad && semanaVisible())
		semana = await conSesion<Semana>(event, 'GET', '/v1/semana').catch(() => null);
	// El plan de hoy, fijo arriba (con DNI: sin DNI no está en ningún plan).
	const hoy = yo?.verificada ? await planDeHoy(event) : null;
	return {
		planes,
		aviso,
		categoria,
		chicas,
		hoy,
		semana: semana && semana.total >= 5 ? semana : null,
		modo
	};
};
