import { redirect } from '@sveltejs/kit';
import { volverSeguro } from '#lib/hosts.ts';
import { api, ErrorApi, ipDe } from '#lib/server/api.ts';
import { COOKIE_VOLVER, guardarSesion } from '#lib/server/sesion.ts';
import { mandarOrigen } from '#lib/server/origen.ts';
import type { Actions } from './$types';

/**
 * Vuelta de Google o Apple por Clerk (y el final del ingreso con mail, que postea acá igual). La página termina el ida y vuelta en el navegador y postea
 * acá el token de sesión de Clerk; Go lo verifica y lo canjea por la sesión de Caigo (cs_…), que
 * queda en la cookie httpOnly. El token de Caigo nunca pasa por el JS del navegador.
 */
export const actions: Actions = {
	default: async (event) => {
		const { request, cookies } = event;
		const f = await request.formData();
		const error = String(f.get('error') ?? '');
		const jwt = String(f.get('token') ?? '');
		if (error || !jwt || jwt.length > 8192)
			redirect(
				303,
				`/ingresar?error=${encodeURIComponent(/^[a-z_]{1,40}$/.test(error) ? error : 'cancelado')}`
			);
		let token: string;
		try {
			const r = await api<{ token: string }>('POST', '/v1/sesion?cliente=web', {
				token: jwt,
				ip: ipDe(event)
			});
			token = r.token;
		} catch (e) {
			const codigo =
				e instanceof ErrorApi && e.estado === 403
					? 'suspendida'
					: e instanceof ErrorApi && e.codigo === 'cuenta_borrada'
						? 'borrada'
						: 'cancelado';
			redirect(303, `/ingresar?error=${codigo}`);
		}
		guardarSesion(cookies, token);
		await mandarOrigen(cookies, token, ipDe(event));
		const volver = volverSeguro(cookies.get(COOKIE_VOLVER));
		cookies.delete(COOKIE_VOLVER, { path: '/' });
		redirect(303, volver);
	}
};
