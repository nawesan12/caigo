import { fail, redirect } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { borrarSesion } from '#lib/server/sesion.ts';
import type { Actions, PageServerLoad } from './$types';

/**
 * Borrar la cuenta: la página pública que piden Google Play y la App Store (se lee sin entrar). Con
 * sesión, además, el botón: se confirma escribiendo el mail de la cuenta y la API lo compara con el
 * de Clerk (DELETE /v1/cuenta {confirmarEmail}).
 */
export const load: PageServerLoad = ({ locals, url }) => ({
	conSesion: !!locals.token,
	listo: url.searchParams.has('listo')
});

export const actions: Actions = {
	default: async (event) => {
		const email = String((await event.request.formData()).get('email') ?? '')
			.trim()
			.slice(0, 200);
		if (!event.locals.token) redirect(303, '/ingresar?volver=%2Fborrar-cuenta');
		if (!email.includes('@'))
			return fail(400, { email, error: 'Escribí el mail con el que entrás a Caigo.' });
		try {
			await conSesion(event, 'DELETE', '/v1/cuenta', { cuerpo: { confirmarEmail: email } });
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { email, error: mensajeDe(e) });
		}
		borrarSesion(event.cookies);
		redirect(303, '/borrar-cuenta?listo');
	}
};
