import { conSesion } from '#lib/server/api.ts';
import type { Semana } from '#lib/tipos.ts';
import type { PageServerLoad } from './$types';

/** «Esta semana en <ciudad>» (informe del 18/11, B2): lo que queda de la semana, día por día. */
export const load: PageServerLoad = async (event) => ({
	semana: await conSesion<Semana>(event, 'GET', '/v1/semana')
});
