import { error, fail } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { lugarAprobado, misLugares, uuid } from '#lib/server/panel.ts';
import type { Asistente, PlanDelLugar, Validacion } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async (event) => {
	const lugar = await lugarAprobado(event);
	const id = uuid(event.params.id);
	const base = `/v1/lugares/${lugar.id}/planes`;
	// No hay "ver un plan" en el panel: se busca en las dos listas.
	const [prox, pas, asist] = await Promise.all([
		conSesion<{ planes: PlanDelLugar[] }>(event, 'GET', `${base}?cual=proximos`),
		conSesion<{ planes: PlanDelLugar[] }>(event, 'GET', `${base}?cual=pasados`),
		conSesion<{ asistentes: Asistente[] }>(event, 'GET', `${base}/${id}/asistentes`).catch((e) => {
			if (e instanceof ErrorApi && e.estado === 404) error(404, 'Ese plan no es de este lugar.');
			throw e;
		})
	]);
	const plan = [...prox.planes, ...pas.planes].find((p) => p.id === id);
	if (!plan) error(404, 'Ese plan no es de este lugar.');
	return { plan, asistentes: asist.asistentes };
};

export const actions: Actions = {
	validar: async (event) => {
		const codigo = String((await event.request.formData()).get('codigo') ?? '')
			.trim()
			.toUpperCase();
		if (!codigo) return fail(400, { error: 'Escribí el código de la entrada.' });
		const { lugar } = await misLugares(event);
		if (!lugar) return fail(400, { error: 'Elegí un lugar.' });
		try {
			const v = await conSesion<Validacion>(event, 'POST', `/v1/lugares/${lugar.id}/validar`, {
				cuerpo: { codigo }
			});
			return { validacion: v };
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
	}
};
