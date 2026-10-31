import { conSesion, ErrorApi } from '#lib/server/api.ts';
import type { Plan } from '#lib/tipos.ts';
import type { PageServerLoad } from './$types';

type Persona = { id: string; nombre: string; fotos: string[] };

/** Si la API dice que no, vacío; lo demás (redirect a ingresar) sigue de largo. */
const o =
	<T>(v: T) =>
	(e: unknown): T => {
		if (e instanceof ErrorApi) return v;
		throw e;
	};

/**
 * Perfil propio (11): suma Mi gente (las caras de la tarjeta) y los planes compartidos, que son
 * los últimos del historial. Si alguna falla, la pantalla igual se ve.
 */
export const load: PageServerLoad = async (event) => {
	const [gente, historial] = await Promise.all([
		conSesion<{ gente: Persona[] }>(event, 'GET', '/v1/gente')
			.then((r) => r.gente)
			.catch(o<Persona[]>([])),
		conSesion<{ planes: Plan[] }>(event, 'GET', '/v1/planes/mios?cual=historial')
			.then((r) => r.planes.filter((p) => !p.cancelado).slice(0, 6))
			.catch(o<Plan[]>([]))
	]);
	return { gente: gente.slice(0, 4), compartidos: historial };
};
