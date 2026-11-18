import { error, json } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { uuid } from '#lib/server/panel.ts';
import type { RequestHandler } from './$types';

/**
 * Los gestos de la tarjeta (Ahora y «Decidí rápido»), por fetch:
 * - `caigo`: POST /v1/planes/{id}/sumarme de a uno (sólo planes que se suman directo; ver sumaDirecto).
 * - `ocultar` / `mostrar`: «No me interesa» y «Deshacer» (POST / DELETE /v1/planes/{id}/ocultar).
 * Sin sesión, o si hay que verificarse, devuelve `ir` para que la página navegue.
 */
export const POST: RequestHandler = async (event) => {
	if (event.request.headers.get('origin') !== event.url.origin) error(403, 'Origen no permitido.');
	const id = uuid(event.params.id);
	const b = (await event.request.json().catch(() => ({}))) as Record<string, unknown>;
	const accion = String(b.accion ?? '');
	if (!event.locals.token)
		return json({ ir: `/ingresar?volver=${encodeURIComponent(`/app/plan/${id}`)}` });
	try {
		if (accion === 'caigo') {
			const r = await conSesion<{ estado: string }>(event, 'POST', `/v1/planes/${id}/sumarme`, {
				cuerpo: { cantidad: 1, mensaje: '' },
				json: true
			});
			return json({ estado: r.estado });
		}
		if (accion === 'ocultar' || accion === 'mostrar') {
			await conSesion(event, accion === 'ocultar' ? 'POST' : 'DELETE', `/v1/planes/${id}/ocultar`, {
				json: true
			});
			return json({ ok: true });
		}
		return json({ error: 'Gesto desconocido.' }, { status: 400 });
	} catch (e) {
		if (!(e instanceof ErrorApi)) throw e;
		if (e.codigo === 'falta_selfie' || e.codigo === 'falta_dni')
			return json({ ir: `/app/verificar?volver=${encodeURIComponent(`/app/plan/${id}`)}` });
		// Lo que pide confirmar algo (género, grupo, entrada): al detalle.
		if (e.estado === 409 || e.estado === 422) return json({ ir: `/app/plan/${id}` });
		return json({ error: mensajeDe(e), codigo: e.codigo }, { status: e.estado });
	}
};
