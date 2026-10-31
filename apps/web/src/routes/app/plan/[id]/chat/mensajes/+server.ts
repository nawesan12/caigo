import { error, json } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { uuid } from '#lib/server/panel.ts';
import type { Mensaje } from '#lib/tipos.ts';
import type { RequestHandler } from './$types';

/**
 * Proxy del chat para el navegador (el token de la sesión no sale del servidor). La página consulta
 * cada 4 s mientras está abierta: el tiempo real por WebSocket de la API pide el token en la URL y
 * eso no se puede hacer sin exponerlo.
 */
export const GET: RequestHandler = async (event) => {
	const id = uuid(event.params.id);
	try {
		const r = await conSesion<{ mensajes: Mensaje[] }>(event, 'GET', `/v1/planes/${id}/mensajes`);
		return json(r, { headers: { 'cache-control': 'no-store' } });
	} catch (e) {
		if (!(e instanceof ErrorApi)) throw e;
		return json({ error: mensajeDe(e) }, { status: e.estado });
	}
};

export const POST: RequestHandler = async (event) => {
	if (event.request.headers.get('origin') !== event.url.origin) error(403, 'Origen no permitido.');
	const id = uuid(event.params.id);
	const { texto } = (await event.request.json().catch(() => ({}))) as { texto?: string };
	if (!texto?.trim()) return json({ error: 'Escribí algo.' }, { status: 400 });
	try {
		const m = await conSesion<Mensaje>(event, 'POST', `/v1/planes/${id}/mensajes`, {
			cuerpo: { texto: texto.trim().slice(0, 1000) }
		});
		return json(m, { status: 201 });
	} catch (e) {
		if (!(e instanceof ErrorApi)) throw e;
		return json({ error: mensajeDe(e) }, { status: e.estado });
	}
};
