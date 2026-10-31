import { fail } from '@sveltejs/kit';
import { api, ErrorApi, ipDe, mensajeDe } from '#lib/server/api.ts';
import type { Actions } from './$types';

/** Botón de arrepentimiento (Resolución 424/2020): sin cuenta, devuelve el código al instante. */
export const actions: Actions = {
	default: async (event) => {
		const f = await event.request.formData();
		const datos = {
			nombre: String(f.get('nombre') ?? '').trim(),
			email: String(f.get('email') ?? '').trim(),
			compra: String(f.get('compra') ?? '').trim()
		};
		try {
			const r = await api<{ codigo: string }>('POST', '/v1/publico/arrepentimiento', {
				cuerpo: datos,
				ip: ipDe(event)
			});
			return { codigo: r.codigo, email: datos.email };
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e), ...datos });
		}
	}
};
