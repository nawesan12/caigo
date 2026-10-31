import { error } from '@sveltejs/kit';
import { baseApi } from '#lib/server/api.ts';
import type { RequestHandler } from './$types';

/**
 * Proxy de archivos privados (DNI y selfies) para el backoffice: el servidor los pide a la API con
 * la sesión de la cookie, así el token nunca va al navegador. La API decide si se pueden ver.
 */
export const GET: RequestHandler = async ({ params, locals }) => {
	if (!/^[0-9a-f-]{36}$/i.test(params.id)) error(404, 'No encontrado');
	if (!locals.token) error(404, 'No encontrado');
	const res = await fetch(`${baseApi()}/v1/archivos/${params.id}`, {
		headers: { authorization: `Bearer ${locals.token}` }
	}).catch(() => null);
	if (!res?.ok || !res.body) error(404, 'No encontrado');
	return new Response(res.body, {
		headers: {
			'content-type': res.headers.get('content-type') ?? 'application/octet-stream',
			'cache-control': 'private, no-store',
			'x-content-type-options': 'nosniff'
		}
	});
};
