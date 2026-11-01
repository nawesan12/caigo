import { error, redirect } from '@sveltejs/kit';
import { conSesion, ErrorApi } from '#lib/server/api.ts';
import { uuid } from '#lib/server/panel.ts';
import { bloquear } from '#lib/server/privados.ts';
import { volverSeguro } from '#lib/hosts.ts';
import type { PerfilPersona } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

/** El perfil de otra persona (GET /v1/personas/{id}): desde Mi gente o el chat privado. */
export const load: PageServerLoad = async (event) => {
	const id = uuid(event.params.id);
	const { yo } = await event.parent();
	if (id === yo.id) redirect(303, '/app/perfil');
	const v = volverSeguro(event.url.searchParams.get('volver'), '/app/perfil/gente');
	try {
		const persona = await conSesion<PerfilPersona>(event, 'GET', `/v1/personas/${id}`);
		return { persona, volver: v.startsWith('/app/') ? v : '/app/perfil/gente' };
	} catch (e) {
		if (e instanceof ErrorApi && [403, 404].includes(e.estado)) error(404, e.detalle);
		throw e;
	}
};

export const actions: Actions = {
	bloquear: async (event) => bloquear(event, event.params.id)
};
