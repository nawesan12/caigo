import { fail, redirect } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { lugarAprobado, misLugares } from '#lib/server/panel.ts';
import type { CobroPlan } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async (event) => {
	const lugar = await lugarAprobado(event);
	const { cobros } = await conSesion<{ cobros: CobroPlan[] }>(event, 'GET', `/v1/lugares/${lugar.id}/cobros`);
	const mp = event.url.searchParams.get('mp');
	return { cobros, mp: mp === 'ok' || mp === 'error' ? mp : null, mpError: event.url.searchParams.get('detalle') ?? '' };
};

export const actions: Actions = {
	conectar: async (event) => {
		const { lugar } = await misLugares(event);
		if (!lugar) return fail(400, { error: 'Elegí un lugar.' });
		let link: string;
		try {
			link = (await conSesion<{ link: string }>(event, 'POST', `/v1/lugares/${lugar.id}/mercadopago`)).link;
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
		redirect(303, link);
	},
	desconectar: async (event) => {
		const { lugar } = await misLugares(event);
		if (!lugar) return fail(400, { error: 'Elegí un lugar.' });
		try {
			await conSesion(event, 'DELETE', `/v1/lugares/${lugar.id}/mercadopago`);
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
		return { desconectado: true };
	}
};
