import { error, fail, redirect } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { ubicacion } from '#lib/server/app.ts';
import { uuid } from '#lib/server/panel.ts';
import type { Plan } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

/** Editar un plan propio que no empezó (contrato §2: PATCH /v1/planes/{id}). */
export const load: PageServerLoad = async (event) => {
	const id = uuid(event.params.id);
	let plan: Plan;
	try {
		plan = await conSesion<Plan>(event, 'GET', `/v1/planes/${id}`);
	} catch (e) {
		if (e instanceof ErrorApi && e.estado === 404) error(404, e.detalle);
		throw e;
	}
	// Sólo quien organiza, un plan propio (no de un lugar) que no empezó ni se canceló.
	if (
		plan.miEstado !== 'organizador' ||
		plan.lugar ||
		plan.cancelado ||
		new Date(plan.empiezaEn).getTime() <= Date.now()
	)
		redirect(303, `/app/plan/${id}`);
	return { plan, centro: ubicacion(event) };
};

export const actions: Actions = {
	default: async (event) => {
		const id = uuid(event.params.id);
		let datos: Record<string, unknown>;
		try {
			datos = JSON.parse(String((await event.request.formData()).get('datos') ?? '{}'));
		} catch {
			return fail(400, { error: 'Revisá el plan.' });
		}
		try {
			await conSesion<Plan>(event, 'PATCH', `/v1/planes/${id}`, { cuerpo: datos });
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			// 409 cupo_menor ("Ya van 4: el cupo no puede ser menor."), empezo, cancelado: tal cual.
			return fail(e.estado === 409 ? 409 : 400, { error: mensajeDe(e) });
		}
		redirect(303, `/app/plan/${id}?editado=1`);
	}
};
