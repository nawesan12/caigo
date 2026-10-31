import { fail } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { lugarAprobado, misLugares } from '#lib/server/panel.ts';
import type { PlanDelLugar } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async (event) => {
	const lugar = await lugarAprobado(event);
	return { mpConectado: lugar.mpConectado };
};

export const actions: Actions = {
	default: async (event) => {
		const f = await event.request.formData();
		// El lugar sale de la sesión (el que está eligiendo en el panel), no del formulario.
		const { lugar } = await misLugares(event);
		const lugarId = lugar?.id ?? '';
		let datos: Record<string, unknown>;
		try {
			datos = JSON.parse(String(f.get('datos') ?? '{}'));
		} catch {
			return fail(400, { error: 'Revisá el plan.' });
		}
		if (!/^[0-9a-f-]{36}$/i.test(lugarId)) return fail(400, { error: 'Elegí un lugar.' });
		try {
			const { id } = await conSesion<{ id: string }>(
				event,
				'POST',
				`/v1/lugares/${lugarId}/planes`,
				{
					cuerpo: datos
				}
			);
			const { planes } = await conSesion<{ planes: PlanDelLugar[] }>(
				event,
				'GET',
				`/v1/lugares/${lugarId}/planes?cual=proximos`
			);
			const p = planes.find((x) => x.id === id);
			return { publicado: { id, titulo: String(datos.titulo ?? ''), link: p?.link ?? '' } };
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
	}
};
