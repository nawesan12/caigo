import { error, fail, redirect } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe, quizasConSesion } from '#lib/server/api.ts';
import { conUbicacion } from '#lib/server/app.ts';
import { uuid } from '#lib/server/panel.ts';
import { aGenero, leerPedido, sumarse } from '#lib/server/sumarse.ts';
import type { Plan } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async (event) => {
	const id = uuid(event.params.id);
	try {
		const plan = await quizasConSesion<Plan>(
			event,
			`/v1/planes/${id}?${conUbicacion(event)}`,
			`/v1/publico/planes/${id}`
		);
		const pago = event.url.searchParams.get('pago');
		return {
			plan,
			pago: pago === 'ok' || pago === 'pendiente' || pago === 'error' ? pago : null,
			publicado: event.url.searchParams.get('publicado') === '1',
			editado: event.url.searchParams.get('editado') === '1'
		};
	} catch (e) {
		if (e instanceof ErrorApi && e.estado === 404) error(404, e.detalle);
		throw e;
	}
};

export const actions: Actions = {
	sumarme: async (event) => {
		const id = uuid(event.params.id);
		const pedido = leerPedido(await event.request.formData());
		let link: string;
		try {
			const r = await sumarse(event, id, pedido);
			if (!r.link) return { estado: r.estado };
			link = r.link;
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			// Sin selfie: a verificarse y de vuelta acá.
			if (e.codigo === 'falta_selfie')
				redirect(303, `/app/verificar?volver=${encodeURIComponent(`/app/plan/${id}`)}`);
			// Plan «Solo mujeres» / «Solo diversidades» y todavía no dijo su género: se pregunta y se
			// reintenta desde esa pantalla.
			if (e.codigo === 'falta_genero') redirect(303, aGenero(id, pedido));
			return fail(e.estado === 400 ? 400 : 409, { error: mensajeDe(e), codigo: e.codigo });
		}
		redirect(303, link);
	},
	salir: async (event) => {
		const id = uuid(event.params.id);
		try {
			await conSesion(event, 'DELETE', `/v1/planes/${id}/sumarme`);
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e), codigo: e.codigo });
		}
		return { estado: 'ninguno' };
	},
	/** El organizador cancela: la API avisa a los que van y a los pendientes, y devuelve lo pagado. */
	cancelar: async (event) => {
		const id = uuid(event.params.id);
		try {
			await conSesion(event, 'DELETE', `/v1/planes/${id}`);
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e), codigo: e.codigo });
		}
		redirect(303, '/app/mis?cancelado=1');
	}
};
