import { conSesion } from '#lib/server/api.ts';
import type { DeMiGente } from '#lib/tipos.ts';
import type { PageServerLoad } from './$types';

/** Mi gente (GET /v1/gente): la gente que sumaste después de un plan, la última primero. */
export const load: PageServerLoad = async (event) => {
	const r = await conSesion<{ gente: DeMiGente[] }>(event, 'GET', '/v1/gente');
	return { gente: r.gente };
};
