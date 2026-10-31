import { dev } from '$app/env';
import { fail, redirect } from '@sveltejs/kit';
import { volverSeguro } from '#lib/hosts.ts';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { INTENCIONES } from '#lib/app.ts';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = ({ url }) => ({
	volver: volverSeguro(url.searchParams.get('volver'), '/app')
});

export const actions: Actions = {
	/** "¿Qué buscás?" y "¿Qué te copa?" del onboarding del Prototipo → PATCH /v1/yo. */
	default: async (event) => {
		const f = await event.request.formData();
		const validas = new Set(INTENCIONES.map((i) => i.valor));
		const intenciones = f
			.getAll('intencion')
			.map(String)
			.filter((i) => validas.has(i));
		const intereses = f
			.getAll('interes')
			.map((x) => String(x).trim().slice(0, 30))
			.filter(Boolean)
			.slice(0, 12);
		if (!intenciones.length) return fail(400, { error: 'Elegí al menos una, después la cambiás.' });
		try {
			await conSesion(event, 'PATCH', '/v1/yo', {
				cuerpo: intereses.length ? { intenciones, intereses } : { intenciones }
			});
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
		event.cookies.set('caigo_intereses', '1', {
			path: '/',
			httpOnly: true,
			secure: !dev,
			sameSite: 'lax',
			maxAge: 365 * 86400
		});
		redirect(303, volverSeguro(String(f.get('volver') ?? ''), '/app'));
	}
};
