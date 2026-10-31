import { error, fail } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { uuid } from '#lib/server/panel.ts';
import type { Plan, Solicitud } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

/** Solicitudes (07): quiénes quieren sumarse a un plan que organizo. */
export const load: PageServerLoad = async (event) => {
	const id = uuid(event.params.id);
	try {
		const [plan, s] = await Promise.all([
			conSesion<Plan>(event, 'GET', `/v1/planes/${id}`),
			conSesion<{ solicitudes: Solicitud[] }>(event, 'GET', `/v1/planes/${id}/solicitudes`)
		]);
		return { plan, solicitudes: s.solicitudes.filter((x) => x.estado === 'pendiente') };
	} catch (e) {
		if (e instanceof ErrorApi && (e.estado === 404 || e.estado === 403)) error(404, e.detalle);
		throw e;
	}
};

export const actions: Actions = {
	default: async (event) => {
		const f = await event.request.formData();
		const sid = uuid(String(f.get('id') ?? ''));
		const accion = f.get('accion') === 'aceptar' ? 'aceptar' : 'rechazar';
		try {
			await conSesion(event, 'POST', `/v1/solicitudes/${sid}/${accion}`);
			return { ok: accion };
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
	}
};
