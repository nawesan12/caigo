import { json } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { uuid } from '#lib/server/panel.ts';
import type { Privado } from '#lib/tipos.ts';
import type { RequestHandler } from './$types';

/**
 * Proxy del hilo para el navegador, como el del chat del plan: la página consulta cada 4 s (el
 * WebSocket de la API pide el token en la URL) y pide los anteriores con `?antes=<id>`.
 */
export const GET: RequestHandler = async (event) => {
	const id = uuid(event.params.id);
	const antes = Number(event.url.searchParams.get('antes'));
	const q = Number.isSafeInteger(antes) && antes > 0 ? `?antes=${antes}` : '';
	try {
		const r = await conSesion<{ privados: Privado[] }>(event, 'GET', `/v1/privados/${id}${q}`);
		return json(r, { headers: { 'cache-control': 'no-store' } });
	} catch (e) {
		if (!(e instanceof ErrorApi)) throw e;
		return json({ error: mensajeDe(e) }, { status: e.estado });
	}
};
