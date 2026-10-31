import { conSesion } from '#lib/server/api.ts';
import { conUbicacion } from '#lib/server/app.ts';
import { CATEGORIAS } from '#lib/plan.ts';
import type { Plan } from '#lib/tipos.ts';
import type { PageServerLoad } from './$types';

/** Explorar (04): la agenda de las próximas dos semanas. */
export const load: PageServerLoad = async (event) => {
	const sp = event.url.searchParams;
	const c = sp.get('categoria') ?? '';
	const categoria = c in CATEGORIAS ? c : '';
	const verificados = sp.get('verificados') === '1';
	const { planes } = await conSesion<{ planes: Plan[] }>(
		event,
		'GET',
		`/v1/planes/explorar?${conUbicacion(event, { categoria, verificados: verificados ? '1' : '' })}`
	);
	return { planes, categoria, verificados, dia: sp.get('dia') ?? '' };
};
