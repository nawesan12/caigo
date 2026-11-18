import { dev } from '$app/env';
import type { RequestEvent } from '@sveltejs/kit';
import { conSesion } from './api.ts';

/** El código de «Traé a alguien» que dejó el link /i/<código> hasta que la persona entra. */
export const COOKIE_REF = 'caigo_ref';

export function guardarInvitacion(event: RequestEvent, codigo: string) {
	event.cookies.set(COOKIE_REF, codigo, {
		path: '/',
		httpOnly: true,
		secure: !dev,
		sameSite: 'lax',
		maxAge: 14 * 86400
	});
}

/**
 * Con sesión, el código guardado se manda una vez (POST /v1/yo/invitado) y se borra. Si la API dice
 * que no (cuenta vieja, ya puso uno, código propio), se descarta igual: no se reintenta.
 */
export async function usarInvitacion(event: RequestEvent): Promise<void> {
	const codigo = event.cookies.get(COOKIE_REF);
	if (!codigo || !event.locals.token) return;
	event.cookies.delete(COOKIE_REF, { path: '/' });
	await conSesion(event, 'POST', '/v1/yo/invitado', { cuerpo: { codigo }, json: true }).catch(
		() => undefined
	);
}
