import { dev } from '$app/env';
import { fail, redirect } from '@sveltejs/kit';
import { volverSeguro } from '#lib/hosts.ts';
import { api, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { COOKIE_VOLVER, guardarSesion, tokenValido } from '#lib/server/sesion.ts';
import { mandarOrigen } from '#lib/server/origen.ts';
import type { Actions, PageServerLoad } from './$types';

const ERRORES: Record<string, string> = {
	cancelado: 'No se completó el ingreso. Probá de nuevo.',
	estado_invalido: 'El ingreso tardó demasiado. Probá de nuevo.',
	clerk_no_responde: 'No pudimos hablar con el servicio de ingreso. Probá en un rato.',
	suspendida: 'Tu cuenta está suspendida. Escribinos a soporte@caigo.com.ar.'
};

export const load: PageServerLoad = ({ url, cookies, locals }) => {
	const volver = volverSeguro(url.searchParams.get('volver'));
	if (locals.token) redirect(303, volver);
	// Se guarda adónde volver mientras dura el ida y vuelta con Google o Apple.
	cookies.set(COOKIE_VOLVER, volver, {
		path: '/',
		httpOnly: true,
		secure: !dev,
		sameSite: 'lax',
		maxAge: 900
	});
	const codigo = url.searchParams.get('error') ?? '';
	return {
		error: codigo ? (ERRORES[codigo] ?? ERRORES.cancelado) : '',
		dev
	};
};

export const actions: Actions = {
	/** Sólo en desarrollo: entrar con un token cs_… creado a mano en la base (ver README). */
	prueba: async ({ request, cookies }) => {
		if (!dev) return fail(404);
		const t = String((await request.formData()).get('token') ?? '').trim();
		if (!tokenValido(t)) return fail(400, { prueba: 'Ese token no tiene la forma cs_….' });
		try {
			await api('GET', '/v1/yo', { token: t });
		} catch (e) {
			return fail(400, {
				prueba: e instanceof ErrorApi && e.estado === 401 ? 'Ese token no existe.' : mensajeDe(e)
			});
		}
		guardarSesion(cookies, t);
		await mandarOrigen(cookies, t);
		const volver = volverSeguro(cookies.get(COOKIE_VOLVER));
		cookies.delete(COOKIE_VOLVER, { path: '/' });
		redirect(303, volver);
	}
};
