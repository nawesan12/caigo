import { error, fail, redirect } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe, quizasConSesion } from '#lib/server/api.ts';
import { conUbicacion } from '#lib/server/app.ts';
import { uuid } from '#lib/server/panel.ts';
import { leerPedido, sumarse } from '#lib/server/sumarse.ts';
import type { Plan } from '#lib/tipos.ts';

type MisPases = { entradas: { id: string; planId: string; estado: string }[] };
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async (event) => {
	const id = uuid(event.params.id);
	const { yo } = await event.parent();
	try {
		const plan = await quizasConSesion<Plan>(
			event,
			`/v1/planes/${id}?${conUbicacion(event, {}, yo?.ciudad)}`,
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
			// Sin selfie o sin DNI: a verificarse y de vuelta acá.
			if (e.codigo === 'falta_selfie' || e.codigo === 'falta_dni')
				redirect(
					303,
					`/app/verificar?motivo=sumarte&volver=${encodeURIComponent(`/app/plan/${id}`)}`
				);
			return fail(e.estado === 400 ? 400 : 409, { error: mensajeDe(e), codigo: e.codigo });
		}
		redirect(303, link, { external: true });
	},
	salir: async (event) => {
		const id = uuid(event.params.id);
		try {
			await conSesion(event, 'DELETE', `/v1/planes/${id}/sumarme`);
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			// Con entrada paga se sale cancelando la entrada (con el reembolso que corresponda).
			if (e.codigo === 'con_entrada')
				return fail(409, {
					error: `${mensajeDe(e)} Para salir, cancelá tu entrada.`,
					codigo: e.codigo
				});
			return fail(400, { error: mensajeDe(e), codigo: e.codigo });
		}
		return { estado: 'ninguno' };
	},
	/** Salir de un plan con entrada: DELETE /v1/entradas/{id} (la API calcula el reembolso). */
	cancelarEntrada: async (event) => {
		const id = uuid(event.params.id);
		let reembolso: number;
		try {
			const { entradas } = await conSesion<MisPases>(event, 'GET', '/v1/pases');
			const e = entradas.find((x) => x.planId === id && x.estado === 'pagada');
			if (!e) return fail(404, { error: 'No encontramos tu entrada para este plan.' });
			const r = await conSesion<{ reembolsoCentavos: number }>(
				event,
				'DELETE',
				`/v1/entradas/${encodeURIComponent(e.id)}`
			);
			reembolso = r?.reembolsoCentavos ?? 0;
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(409, { error: mensajeDe(e), codigo: e.codigo });
		}
		return { estado: 'ninguno', reembolso };
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
