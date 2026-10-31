import { fail } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { ubicacion } from '#lib/server/app.ts';
import type { Plan } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = (event) => ({ centro: ubicacion(event) });

export const actions: Actions = {
	default: async (event) => {
		let datos: Record<string, unknown>;
		try {
			datos = JSON.parse(String((await event.request.formData()).get('datos') ?? '{}'));
		} catch {
			return fail(400, { error: 'Revisá el plan.' });
		}
		try {
			const p = await conSesion<Plan>(event, 'POST', '/v1/planes', { cuerpo: datos });
			return { publicado: { id: p.id, slug: p.slug, titulo: p.titulo } };
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
	}
};
