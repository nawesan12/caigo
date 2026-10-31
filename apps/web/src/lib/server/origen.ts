import type { Cookies } from '@sveltejs/kit';
import { dev } from '$app/env';
import { api } from './api.ts';
import { ORIGEN_VALIDO, origenDe } from '#lib/origen.ts';

/**
 * La cookie del origen: se guarda la primera vez que alguien entra con ?utm_source= o ?ref=, o
 * abriendo el link de un plan (/p/<slug>), y se manda una sola vez con PATCH /v1/yo después de
 * ingresar.
 */
export const COOKIE_ORIGEN = 'caigo_origen';

/** Guarda el origen si es la primera vez (gana el primer contacto). Devuelve si lo guardó. */
export function guardarOrigen(cookies: Cookies, url: URL): boolean {
	if (cookies.get(COOKIE_ORIGEN)) return false;
	const o = origenDe(url);
	if (!o) return false;
	cookies.set(COOKIE_ORIGEN, o, {
		path: '/',
		httpOnly: true,
		secure: !dev,
		sameSite: 'lax',
		maxAge: 30 * 86400
	});
	return true;
}

/** Después de ingresar: manda el origen (si hay) y borra la cookie. Si la API falla, no importa. */
export async function mandarOrigen(cookies: Cookies, token: string, ip?: string | null) {
	const o = cookies.get(COOKIE_ORIGEN);
	if (!o) return;
	cookies.delete(COOKIE_ORIGEN, { path: '/' });
	if (!ORIGEN_VALIDO.test(o)) return;
	try {
		await api('PATCH', '/v1/yo', { token, ip, cuerpo: { origen: o } });
	} catch {
		// La API todavía no lo acepta, o ya tenía origen: no frena el ingreso.
	}
}
