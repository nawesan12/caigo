import { error, fail, redirect } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { uuid } from '#lib/server/panel.ts';
import { bloquear, PAGINA } from '#lib/server/privados.ts';
import type { PerfilPersona, Privado } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

/**
 * Un chat privado. La persona sale de su perfil (si llega desde un aviso o un link no hay otra
 * forma de saber el nombre); traer el hilo ya marca como leído lo que te mandó. Si está bloqueada
 * o no existe, la API dice 404.
 */
export const load: PageServerLoad = async (event) => {
	const id = uuid(event.params.id);
	const { yo } = await event.parent();
	if (id === yo.id) redirect(303, '/app/mensajes');
	try {
		const [persona, h] = await Promise.all([
			conSesion<PerfilPersona>(event, 'GET', `/v1/personas/${id}`),
			conSesion<{ privados: Privado[] }>(event, 'GET', `/v1/privados/${id}`)
		]);
		return { persona, privados: h.privados, hayMas: h.privados.length >= PAGINA };
	} catch (e) {
		if (e instanceof ErrorApi && [403, 404].includes(e.estado)) error(404, e.detalle);
		throw e;
	}
};

export const actions: Actions = {
	/** Mandar (con enhance; sin JS, la página vuelve a cargar con el mensaje). */
	mandar: async (event) => {
		const id = uuid(event.params.id);
		const texto = String((await event.request.formData()).get('texto') ?? '').trim();
		if (!texto) return fail(400, { error: 'Escribí algo.' });
		try {
			const privado = await conSesion<Privado>(event, 'POST', `/v1/privados/${id}`, {
				cuerpo: { texto: texto.slice(0, 2000) }
			});
			return { privado };
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			// 403: no pueden hablar (no compartieron un plan, no es de su gente, no hay match).
			return fail(e.estado === 403 ? 403 : 400, { error: mensajeDe(e), texto });
		}
	},
	bloquear: async (event) => bloquear(event, event.params.id)
};
