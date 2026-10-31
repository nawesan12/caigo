import { fail, redirect } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { borrarSesion } from '#lib/server/sesion.ts';
import type { Actions } from './$types';

/** Borrar la cuenta (lo piden las tiendas, también desde la web): DELETE /v1/yo y afuera. */
export const actions: Actions = {
	default: async (event) => {
		try {
			await conSesion(event, 'DELETE', '/v1/yo');
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
		borrarSesion(event.cookies);
		redirect(303, '/?baja=1');
	}
};
