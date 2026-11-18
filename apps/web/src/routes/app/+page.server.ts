import { planesQuizas } from '#lib/server/api.ts';
import { conUbicacion } from '#lib/server/app.ts';
import { FILTROS_AHORA } from '#lib/app.ts';
import type { PageServerLoad } from './$types';

/** Ahora · lista (02): los planes de las próximas 3 horas cerca. */
export const load: PageServerLoad = async (event) => {
	const c = event.url.searchParams.get('categoria') ?? '';
	const categoria = FILTROS_AHORA.some((f) => f.valor === c) ? c : '';
	const { yo } = await event.parent();
	const q = conUbicacion(event, { categoria }, yo?.ciudad);
	const { planes, aviso } = await planesQuizas(
		event,
		`/v1/planes/ahora?${q}`,
		`/v1/publico/planes/ahora?${q}`
	);
	return { planes, aviso, categoria };
};
