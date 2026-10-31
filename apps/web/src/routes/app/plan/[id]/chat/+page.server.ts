import { error, fail, redirect } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { uuid } from '#lib/server/panel.ts';
import type { Mensaje, Plan } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

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

/** El menú del chat: salir del plan o, quien organiza, cancelarlo. Los dos piden confirmar antes. */
export const actions: Actions = {
	salir: async (event) => {
		const id = uuid(event.params.id);
		try {
			await conSesion(event, 'DELETE', `/v1/planes/${id}/sumarme`);
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
		redirect(303, '/app/mis?cual=me_sume&salio=1');
	},
	cancelar: async (event) => {
		const id = uuid(event.params.id);
		try {
			await conSesion(event, 'DELETE', `/v1/planes/${id}`);
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
		redirect(303, '/app/mis?cancelado=1');
	}
};
