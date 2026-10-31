import { fail } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { uuid } from '#lib/server/panel.ts';
import type { Bloqueado } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

/** Bloqueados (contrato §4). Si la API todavía no tiene la lista, se ve vacía. */
export const load: PageServerLoad = async (event) => {
	try {
		const r = await conSesion<{ bloqueos: Bloqueado[] }>(event, 'GET', '/v1/bloqueos');
		return { bloqueos: r?.bloqueos ?? [] };
	} catch (e) {
		if (e instanceof ErrorApi) return { bloqueos: [] as Bloqueado[] };
		throw e;
	}
};

export const actions: Actions = {
	desbloquear: async (event) => {
		const id = uuid(String((await event.request.formData()).get('id') ?? ''));
		try {
			await conSesion(event, 'DELETE', `/v1/bloqueos/${id}`);
			return { ok: true };
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
	}
};
