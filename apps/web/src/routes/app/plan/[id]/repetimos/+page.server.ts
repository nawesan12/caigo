import { error, fail } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { uuid } from '#lib/server/panel.ts';
import type { Plan } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

/** ¿Repetimos? (09b): después del plan. El organizador decide si repite; todos, a quién volverían a ver. */
export const load: PageServerLoad = async (event) => {
	const id = uuid(event.params.id);
	try {
		const plan = await conSesion<Plan>(event, 'GET', `/v1/planes/${id}`);
		return { plan };
	} catch (e) {
		if (e instanceof ErrorApi && e.estado === 404) error(404, e.detalle);
		throw e;
	}
};

export const actions: Actions = {
	default: async (event) => {
		const id = uuid(event.params.id);
		const f = await event.request.formData();
		const repetir = f.get('repetir') === 'si';
		const volveria = f
			.getAll('si')
			.map(String)
			.filter((x) => /^[0-9a-f-]{36}$/i.test(x))
			.slice(0, 60)
			.map((usuarioId) => ({ usuarioId, si: true }));
		try {
			const r = await conSesion<{ nuevo?: Plan | null }>(
				event,
				'POST',
				`/v1/planes/${id}/repetimos`,
				{ cuerpo: { repetir, volveria } }
			);
			return { listo: true, nuevo: r?.nuevo ? { id: r.nuevo.id } : null, gente: volveria.length };
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(e.estado === 409 ? 409 : 400, { error: mensajeDe(e) });
		}
	}
};
