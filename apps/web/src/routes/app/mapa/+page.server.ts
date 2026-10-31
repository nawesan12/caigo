import { quizasConSesion } from '#lib/server/api.ts';
import { conUbicacion, ubicacion } from '#lib/server/app.ts';
import type { Plan } from '#lib/tipos.ts';
import type { PageServerLoad } from './$types';

const FILTROS = ['', 'deporte', 'previa', 'juego'];

/** Ahora · mapa (03). */
export const load: PageServerLoad = async (event) => {
	const c = event.url.searchParams.get('categoria') ?? '';
	const categoria = FILTROS.includes(c) ? c : '';
	const q = conUbicacion(event, { categoria, radio: '30' });
	const { planes } = await quizasConSesion<{ planes: Plan[] }>(
		event,
		`/v1/planes/ahora?${q}`,
		`/v1/publico/planes/ahora?${q}`
	);
	return { planes, categoria, centro: ubicacion(event) };
};
