import type { Cookies } from '@sveltejs/kit';
import { dev } from '$app/env';

/** La sesión de Caigo (`cs_…`) en una cookie httpOnly, 60 días, como la API. */
export const COOKIE = 'caigo_sesion';
const DIAS = 60;

export function guardarSesion(cookies: Cookies, token: string) {
	cookies.set(COOKIE, token, {
		path: '/',
		httpOnly: true,
		// En local (http://localhost) el navegador igual la acepta; en producción va siempre Secure.
		secure: !dev,
		sameSite: 'lax',
		maxAge: DIAS * 86400
	});
}

export function borrarSesion(cookies: Cookies) {
	cookies.delete(COOKIE, { path: '/', httpOnly: true, secure: !dev, sameSite: 'lax' });
}

/** Un token propio de Caigo tiene esta forma; cualquier otra cosa en la cookie no se manda. */
export function tokenValido(t: string | undefined | null): t is string {
	return !!t && /^cs_[A-Za-z0-9_-]{10,200}$/.test(t);
}

/** Adónde volver después de ingresar (cookie corta, sólo mientras dura el ida y vuelta). */
export const COOKIE_VOLVER = 'caigo_volver';
