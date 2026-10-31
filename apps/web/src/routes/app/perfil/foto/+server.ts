import { error, json } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import type { RequestHandler } from './$types';

const TIPOS = ['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif'];

/**
 * Sube una foto del perfil: el navegador manda el archivo crudo y el servidor lo pasa a
 * POST /v1/archivos?tipo=foto con la sesión (el token nunca llega al navegador).
 */
export const POST: RequestHandler = async (event) => {
	// Es un cuerpo crudo (no un formulario): SvelteKit no chequea el origen, así que se chequea acá.
	if (event.request.headers.get('origin') !== event.url.origin) error(403, 'Origen no permitido.');
	const tipo = (event.request.headers.get('content-type') ?? '').split(';')[0].trim();
	if (!TIPOS.includes(tipo))
		return json({ error: 'Subí una foto (JPG, PNG o WebP).' }, { status: 400 });
	const cuerpo = await event.request.arrayBuffer();
	if (cuerpo.byteLength > 12 * 1024 * 1024)
		return json({ error: 'La foto es muy pesada (hasta 12 MB).' }, { status: 413 });
	try {
		const a = await conSesion<{ id: string; url: string }>(
			event,
			'POST',
			'/v1/archivos?tipo=foto',
			{
				crudo: cuerpo,
				tipo
			}
		);
		return json(a);
	} catch (e) {
		if (!(e instanceof ErrorApi)) throw e;
		return json({ error: mensajeDe(e) }, { status: e.estado });
	}
};
