import { conSesion } from '#lib/server/api.ts';
import { conUbicacion, ubicacion } from '#lib/server/app.ts';
import type { Plan } from '#lib/tipos.ts';
import type { PageServerLoad } from './$types';

const FILTROS = ['', 'deporte', 'previa', 'juego'];

/** Ahora · mapa (03). */
export const load: PageServerLoad = async (event) => {
	const c = event.url.searchParams.get('categoria') ?? '';
	const categoria = FILTROS.includes(c) ? c : '';
	const { planes } = await conSesion<{ planes: Plan[] }>(
		event,
		'GET',
		`/v1/planes/ahora?${conUbicacion(event, { categoria, radio: '30' })}`
	);
	return { planes, categoria, centro: ubicacion(event) };
};
