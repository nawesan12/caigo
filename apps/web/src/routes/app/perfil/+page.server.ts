import { fail } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import type { Actions } from './$types';

export const actions: Actions = {
	default: async (event) => {
		const f = await event.request.formData();
		const intereses = f
			.getAll('interes')
			.map((x) => String(x).trim())
			.filter(Boolean)
			.slice(0, 12);
		try {
			await conSesion(event, 'PATCH', '/v1/yo', {
				cuerpo: {
					nombre: String(f.get('nombre') ?? '').trim(),
					ciudad: String(f.get('ciudad') ?? '').trim(),
					zona: String(f.get('zona') ?? '').trim(),
					intereses
				}
			});
			return { ok: true };
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
	}
};
