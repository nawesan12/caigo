import { redirect } from '@sveltejs/kit';
import { misLugares } from '#lib/server/panel.ts';
import type { LayoutServerLoad } from './$types';

/** El panel de lugares (lugares.caigo.com.ar) pide sesión siempre. */
export const load: LayoutServerLoad = async (event) => {
	const { lugares, lugar } = await misLugares(event);
	// Sin ningún lugar, lo primero es registrarlo.
	if (!lugar && event.route.id !== '/panel/registro') redirect(303, '/registro');
	return { lugares, lugar };
};
