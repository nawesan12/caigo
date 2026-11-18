import { error, json } from '@sveltejs/kit';
import { conSesion, ErrorApi, FOTO_MAX, FOTO_PESADA, mensajeDe } from '#lib/server/api.ts';
import type { RequestHandler } from './$types';

/**
 * Una foto del DNI (frente o dorso), ya pasada a JPEG en el navegador: se sube como `tipo=dni`
 * (cifrada, sólo la ven la persona y el equipo) y devuelve el id para mandarlo con el número.
 */
export const POST: RequestHandler = async (event) => {
	if (event.request.headers.get('origin') !== event.url.origin) error(403, 'Origen no permitido.');
	const tipo = (event.request.headers.get('content-type') ?? '').split(';')[0].trim();
	if (!['image/jpeg', 'image/png', 'image/webp'].includes(tipo))
		return json({ error: 'Subí una foto (JPG o PNG).' }, { status: 400 });
	const cuerpo = await event.request.arrayBuffer();
	if (cuerpo.byteLength > FOTO_MAX) return json({ error: FOTO_PESADA }, { status: 413 });
	try {
		const a = await conSesion<{ id: string }>(event, 'POST', '/v1/archivos?tipo=dni', {
			crudo: cuerpo,
			tipo,
			json: true
		});
		return json({ id: a.id });
	} catch (e) {
		if (!(e instanceof ErrorApi)) throw e;
		return json({ error: mensajeDe(e) }, { status: e.estado });
	}
};
