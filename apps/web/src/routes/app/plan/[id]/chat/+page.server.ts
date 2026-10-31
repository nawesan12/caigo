import { error } from '@sveltejs/kit';
import { conSesion, ErrorApi } from '#lib/server/api.ts';
import { uuid } from '#lib/server/panel.ts';
import type { Mensaje, Plan } from '#lib/tipos.ts';
import type { PageServerLoad } from './$types';

/** Chat del plan (08). */
export const load: PageServerLoad = async (event) => {
	const id = uuid(event.params.id);
	try {
		const [plan, m] = await Promise.all([
			conSesion<Plan>(event, 'GET', `/v1/planes/${id}`),
			conSesion<{ mensajes: Mensaje[] }>(event, 'GET', `/v1/planes/${id}/mensajes`)
		]);
		return { plan, mensajes: m.mensajes };
	} catch (e) {
		if (e instanceof ErrorApi && [403, 404, 409].includes(e.estado)) error(404, e.detalle);
		throw e;
	}
};
