import { error, json } from '@sveltejs/kit';
import { dev } from '$app/env';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { COOKIE_SELFIE } from '#lib/server/cuenta.ts';
import type { RequestHandler } from './$types';

/**
 * La selfie de la verificación (después de la prueba de vida): el navegador manda la foto cruda, el
 * servidor la sube como `tipo=selfie` (privada: sólo la ven la persona y el equipo) y marca la
 * verificación. El id queda en una cookie httpOnly para mandarlo con el DNI (paso 3).
 * `?para=dni`: ya tiene la selfie verificada y sólo hace falta una nueva para el DNI.
 */
export const POST: RequestHandler = async (event) => {
	if (event.request.headers.get('origin') !== event.url.origin) error(403, 'Origen no permitido.');
	const tipo = (event.request.headers.get('content-type') ?? '').split(';')[0].trim();
	if (!['image/jpeg', 'image/png', 'image/webp'].includes(tipo))
		return json({ error: 'Subí una foto (JPG o PNG).' }, { status: 400 });
	const cuerpo = await event.request.arrayBuffer();
	if (cuerpo.byteLength > 12 * 1024 * 1024)
		return json({ error: 'La foto es muy pesada.' }, { status: 413 });
	try {
		const a = await conSesion<{ id: string }>(event, 'POST', '/v1/archivos?tipo=selfie', {
			crudo: cuerpo,
			tipo,
			json: true
		});
		if (event.url.searchParams.get('para') !== 'dni')
			await conSesion(event, 'POST', '/v1/verificacion/selfie', {
				cuerpo: { archivoId: a.id },
				json: true
			});
		event.cookies.set(COOKIE_SELFIE, a.id, {
			path: '/app/verificar',
			httpOnly: true,
			secure: !dev,
			sameSite: 'lax',
			maxAge: 86400
		});
		return json({ ok: true });
	} catch (e) {
		if (!(e instanceof ErrorApi)) throw e;
		return json({ error: mensajeDe(e) }, { status: e.estado });
	}
};
