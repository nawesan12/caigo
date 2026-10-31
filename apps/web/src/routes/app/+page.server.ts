import { conSesion } from '#lib/server/api.ts';
import { conUbicacion } from '#lib/server/app.ts';
import { FILTROS_AHORA } from '#lib/app.ts';
import type { Plan } from '#lib/tipos.ts';
import type { PageServerLoad } from './$types';

/** Ahora · lista (02): los planes de las próximas 3 horas cerca. */
export const load: PageServerLoad = async (event) => {
	const c = event.url.searchParams.get('categoria') ?? '';
	const categoria = FILTROS_AHORA.some((f) => f.valor === c) ? c : '';
	const { planes } = await conSesion<{ planes: Plan[] }>(
		event,
		'GET',
		`/v1/planes/ahora?${conUbicacion(event, { categoria })}`
	);
	return { planes, categoria };
};
