import { error, fail, redirect } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe, quizasConSesion } from '#lib/server/api.ts';
import { conUbicacion } from '#lib/server/app.ts';
import { uuid } from '#lib/server/panel.ts';
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
			publicado: event.url.searchParams.get('publicado') === '1'
		};
	} catch (e) {
		if (e instanceof ErrorApi && e.estado === 404) error(404, e.detalle);
		throw e;
	}
};

export const actions: Actions = {
	sumarme: async (event) => {
		const id = uuid(event.params.id);
		const f = await event.request.formData();
		const cantidad = Math.min(10, Math.max(1, Number(f.get('cantidad')) || 1));
		const mensaje = String(f.get('mensaje') ?? '')
			.trim()
			.slice(0, 200);
		let link: string;
		try {
			const plan = await conSesion<Plan>(event, 'GET', `/v1/planes/${id}`);
			if (plan.lugar && plan.cobro !== 'gratis') {
				// Entrada o seña de un lugar: Checkout Pro de Mercado Pago (vuelve a /p/<slug>?pago=…).
				const c = await conSesion<{ link?: string }>(event, 'POST', `/v1/planes/${id}/entradas`, {
					cuerpo: { cantidad }
				});
				link = c?.link ?? '';
				if (!link) return { estado: 'aceptada' };
			} else {
				const r = await conSesion<{ estado: string }>(event, 'POST', `/v1/planes/${id}/sumarme`, {
					cuerpo: { cantidad, mensaje }
				});
				return { estado: r.estado };
			}
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			// Sin selfie: a verificarse y de vuelta acá.
			if (e.codigo === 'falta_selfie')
				redirect(303, `/app/verificar?volver=${encodeURIComponent(`/app/plan/${id}`)}`);
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
	}
};
