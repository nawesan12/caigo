import { fail } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import type { Invitacion } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

/** «Traé a alguien»: el link propio y, en una cuenta nueva, el código de quien te invitó. */
export const load: PageServerLoad = async (event) => ({
	inv: await conSesion<Invitacion>(event, 'GET', '/v1/yo/invitar')
});

export const actions: Actions = {
	codigo: async (event) => {
		const codigo = String((await event.request.formData()).get('codigo') ?? '')
			.trim()
			.toLowerCase()
			.slice(0, 20);
		if (!codigo) return fail(400, { error: 'Poné el código.' });
		try {
			await conSesion(event, 'POST', '/v1/yo/invitado', { cuerpo: { codigo } });
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
		return { listo: true };
	}
};
