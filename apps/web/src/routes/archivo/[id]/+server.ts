import { error } from '@sveltejs/kit';
import { baseApi, ipDe } from '#lib/server/api.ts';
import type { RequestHandler } from './$types';

/**
 * Proxy de archivos privados (DNI y selfies) para el backoffice: el servidor los pide a la API con
 * la sesión de la cookie, así el token nunca va al navegador. La API decide si se pueden ver y anota
 * cada vez que alguien del equipo abre uno ajeno, con la IP de quien mira (no la de este servidor).
 */
export const GET: RequestHandler = async (event) => {
	const { params, locals } = event;
	if (!/^[0-9a-f-]{36}$/i.test(params.id)) error(404, 'No encontrado');
	if (!locals.token) error(404, 'No encontrado');
	const headers: Record<string, string> = { authorization: `Bearer ${locals.token}` };
	const ip = ipDe(event);
	if (ip) headers['cf-connecting-ip'] = ip;
	const res = await fetch(`${baseApi()}/v1/archivos/${params.id}`, { headers }).catch(() => null);
	if (!res?.ok || !res.body) error(404, 'No encontrado');
	return new Response(res.body, {
		headers: {
			'content-type': res.headers.get('content-type') ?? 'application/octet-stream',
			'cache-control': 'private, no-store',
			'x-content-type-options': 'nosniff'
		}
	});
};
