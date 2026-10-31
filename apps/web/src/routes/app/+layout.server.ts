import { redirect } from '@sveltejs/kit';
import { conSesion } from '#lib/server/api.ts';
import { COOKIE_UBIC, leerUbicacion } from '#lib/app.ts';
import type { Verificacion, Yo } from '#lib/tipos.ts';
import type { LayoutServerLoad } from './$types';

/**
 * Caigo desde la web. Pide sesión (Google o Apple) y, una sola vez, la verificación: teléfono y
 * selfie. Sin eso la API no deja sumarse a planes.
 */
export const load: LayoutServerLoad = async (event) => {
	const [yo, verif] = await Promise.all([
		conSesion<Yo>(event, 'GET', '/v1/yo'),
		conSesion<Verificacion>(event, 'GET', '/v1/verificacion')
	]);
	const verificada = !!verif.telefono && verif.selfie;
	if (!verificada && event.route.id !== '/app/verificar') {
		const volver = event.url.pathname + event.url.search;
		redirect(303, `/app/verificar?volver=${encodeURIComponent(volver)}`);
	}
	event.setHeaders({ 'cache-control': 'private, no-store' });
	return { yo, verif, ubic: leerUbicacion(event.cookies.get(COOKIE_UBIC)) };
};
