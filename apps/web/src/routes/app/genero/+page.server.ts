import { fail, redirect } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { uuid } from '#lib/server/panel.ts';
import { aGenero, leerPedido, sumarse } from '#lib/server/sumarse.ts';
import type { Actions, PageServerLoad } from './$types';

const GENEROS = ['mujer', 'varon', 'no_binario'];

/**
 * «¿Con qué género te identificás?»: la API la pide (422 falta_genero) al sumarse a un plan «Solo
 * mujeres» o «Solo diversidades». Se guarda en el perfil (privado) y se reintenta el pedido.
 */
export const load: PageServerLoad = async (event) => {
	const q = event.url.searchParams;
	const yo = await conSesion<{ genero?: string; diversidad?: boolean }>(event, 'GET', '/v1/yo');
	return {
		plan: q.get('plan') ?? '',
		pedido: leerPedido(q),
		genero: yo.genero ?? '',
		diversidad: !!yo.diversidad
	};
};

export const actions: Actions = {
	default: async (event) => {
		const f = await event.request.formData();
		const genero = String(f.get('genero') ?? '');
		const diversidad = f.get('diversidad') === 'si';
		if (!GENEROS.includes(genero)) return fail(400, { error: 'Elegí una opción.' });
		const plan = String(f.get('plan') ?? '');
		try {
			await conSesion(event, 'PATCH', '/v1/yo', { cuerpo: { genero, diversidad } });
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
		if (!plan) redirect(303, '/app/perfil');
		const id = uuid(plan);
		const pedido = leerPedido(f);
		let link: string;
		try {
			const r = await sumarse(event, id, pedido);
			if (!r.link) redirect(303, `/app/plan/${id}`);
			link = r.link;
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			if (e.codigo === 'falta_selfie')
				redirect(303, `/app/verificar?volver=${encodeURIComponent(aGenero(id, pedido))}`);
			// solo_mujeres / solo_diversidades: el plan no es para esta persona.
			return fail(409, { error: mensajeDe(e), codigo: e.codigo, plan: id });
		}
		redirect(303, link, { external: true });
	}
};
