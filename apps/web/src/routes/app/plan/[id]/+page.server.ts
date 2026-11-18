import { error, fail, redirect } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe, quizasConSesion } from '#lib/server/api.ts';
import { conUbicacion } from '#lib/server/app.ts';
import { uuid } from '#lib/server/panel.ts';
import { leerPedido, sumarse } from '#lib/server/sumarse.ts';
import { enElDia } from '#lib/dia.ts';
import type { Plan, TodoBien } from '#lib/tipos.ts';

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
		// El día del plan: «¿Todo bien?» con los contactos del perfil (si no se puede leer, la hoja
		// ofrece sumar uno).
		let todoBien: TodoBien | null = null;
		if (
			event.locals.token &&
			(plan.miEstado === 'aceptada' || plan.miEstado === 'organizador') &&
			enElDia(plan)
		)
			todoBien = await conSesion<TodoBien>(event, 'GET', `/v1/planes/${id}/todo-bien`).catch(
				() => null
			);
		return {
			plan,
			todoBien,
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
	/** «¿Seguís yendo?» → Voy. */
	confirmo: async (event) => accion(event, 'POST', 'confirmo'),
	/** Plan completo: a la lista de espera (con lo que confirma para «Solo mujeres» / «Solo diversidades»). */
	espera: async (event) => {
		const pedido = leerPedido(await event.request.formData());
		return accion(event, 'POST', 'espera', {
			cantidad: pedido.cantidad,
			mujer: pedido.mujer,
			diversidad: pedido.diversidad
		});
	},
	dejarEspera: async (event) => accion(event, 'DELETE', 'espera'),
	todoBien: async (event) => {
		const contacto = Number((await event.request.formData()).get('contacto') ?? 0);
		return accion(event, 'POST', 'todo-bien', {
			contacto: Number.isFinite(contacto) ? contacto : 0
		});
	},
	apagarTodoBien: async (event) => accion(event, 'DELETE', 'todo-bien'),
	/** «Llegué» desde el navegador: check-in por ubicación (la API mira que estés a 150 m). */
	llegue: async (event) => {
		const f = await event.request.formData();
		const lat = Number(f.get('lat')),
			lng = Number(f.get('lng'));
		if (!Number.isFinite(lat) || !Number.isFinite(lng) || (!lat && !lng))
			return fail(400, { error: 'No pudimos saber dónde estás.' });
		return accion(event, 'POST', 'checkin', { metodo: 'geo', lat, lng });
	},
	/** «Llego en 10»: un mensaje en el chat del plan (pasa por la moderación como cualquier otro). */
	llegoEn: async (event) => {
		const id = uuid(event.params.id);
		try {
			await conSesion(event, 'POST', `/v1/planes/${id}/mensajes`, {
				cuerpo: { texto: 'Llego en 10' }
			});
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
		return { listo: true };
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

/** Una acción simple sobre el plan (`/v1/planes/{id}/<ruta>`): los errores vuelven como { error }. */
async function accion(
	event: Parameters<Actions[string]>[0],
	metodo: 'POST' | 'DELETE',
	ruta: string,
	cuerpo?: unknown
) {
	const id = uuid(event.params.id);
	try {
		await conSesion(event, metodo, `/v1/planes/${id}/${ruta}`, cuerpo ? { cuerpo } : {});
	} catch (e) {
		if (!(e instanceof ErrorApi)) throw e;
		return fail(e.estado === 409 || e.estado === 422 ? 409 : 400, {
			error: mensajeDe(e),
			codigo: e.codigo
		});
	}
	return { listo: true };
}
