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
		// Vacío es vacío: el ejemplo del campo («café de regalo») nunca se guarda como beneficio.
		if (typeof datos.beneficio === 'string') datos.beneficio = datos.beneficio.trim();
		// Desde el 16/11 toda cuenta tiene el DNI verificado: «Solo verificados» ya no existe.
		if (datos.quien !== 'mujeres') datos.quien = 'cualquiera';
		delete datos.soloVerificados;
		let id: string;
		try {
			({ id } = await conSesion<{ id: string }>(event, 'POST', `/v1/lugares/${lugarId}/planes`, {
				cuerpo: datos
			}));
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
		// El plan ya está publicado: si la lista para sacar el link falla, igual se muestra el éxito
		// (antes ese error dejaba la pantalla en el paso 4 con el plan ya creado).
		const link = await conSesion<{ planes: PlanDelLugar[] }>(
			event,
			'GET',
			`/v1/lugares/${lugarId}/planes?cual=proximos`
		)
			.then((r) => r.planes.find((x) => x.id === id)?.link ?? '')
			.catch(() => '');
		return { publicado: { id, titulo: String(datos.titulo ?? ''), link } };
	}
};
