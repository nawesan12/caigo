import { redirect } from '@sveltejs/kit';
import { conSesion } from '#lib/server/api.ts';
import { COOKIE_UBIC, leerUbicacion } from '#lib/app.ts';
import type { Verificacion, Yo } from '#lib/tipos.ts';
import type { LayoutServerLoad } from './$types';

/** Tema de /app (perfil › ajustes): claro, oscuro o como el teléfono. */
const TEMAS = ['claro', 'oscuro', 'sistema'] as const;
type Tema = (typeof TEMAS)[number];

/** Lo que se puede mirar sin cuenta: Ahora (lista y mapa), Explorar y el detalle de un plan. */
const SIN_CUENTA = new Set([
	'/app',
	'/app/mapa',
	'/app/explorar',
	'/app/plan/[id]',
	'/app/bienvenida'
]);

/** Onboarding (01a–01c y "¿Qué buscás?"): cookies de "ya lo vio". */
const COOKIE_BIENVENIDA = 'caigo_bienvenida';
const COOKIE_INTERESES = 'caigo_intereses';
const AL_PASO = new Set(['/app/verificar', '/app/borrar', '/app/bienvenida', '/app/intereses']);

/**
 * Caigo desde la web. Sin cuenta se pueden mirar los planes; para sumarse, crear o chatear pide
 * sesión (Google o Apple) y, una sola vez, la verificación: teléfono y selfie.
 */
export const load: LayoutServerLoad = async (event) => {
	const t = event.cookies.get('caigo_tema') as Tema;
	const tema: Tema = TEMAS.includes(t) ? t : 'claro';
	const ruta = event.route.id ?? '';
	// La primera vez que alguien sin cuenta abre Ahora, las tres slides de bienvenida.
	if (!event.locals.token && ruta === '/app' && !event.cookies.get(COOKIE_BIENVENIDA))
		redirect(303, '/app/bienvenida');
	if (!event.locals.token && SIN_CUENTA.has(ruta)) {
		event.setHeaders({ 'cache-control': 'private, no-store' });
		// Sólo las pantallas de SIN_CUENTA llegan acá sin sesión, y no usan `yo` ni `verif` (lo
		// tratan como opcional). Las demás siempre tienen sesión: el tipo queda sin null para ellas.
		return {
			yo: null as unknown as Yo,
			verif: null as unknown as Verificacion,
			anonimo: true,
			tema,
			ubic: leerUbicacion(event.cookies.get(COOKIE_UBIC))
		};
	}
	const [yo, verif] = await Promise.all([
		conSesion<Yo>(event, 'GET', '/v1/yo'),
		conSesion<Verificacion>(event, 'GET', '/v1/verificacion')
	]);
	const verificada = !!verif.telefono && verif.selfie;
	// Borrar la cuenta no pide verificarse antes.
	if (!verificada && event.route.id !== '/app/verificar' && event.route.id !== '/app/borrar') {
		const volver = event.url.pathname + event.url.search;
		redirect(303, `/app/verificar?volver=${encodeURIComponent(volver)}`);
	}
	// Ya verificada, una vez: qué busca y qué le copa (intenciones e intereses).
	const intenciones = (yo as Yo & { intenciones?: string[] }).intenciones ?? [];
	if (!intenciones.length && !AL_PASO.has(ruta) && !event.cookies.get(COOKIE_INTERESES)) {
		const volver = event.url.pathname + event.url.search;
		redirect(303, `/app/intereses?volver=${encodeURIComponent(volver)}`);
	}
	event.setHeaders({ 'cache-control': 'private, no-store' });
	return { yo, verif, anonimo: false, tema, ubic: leerUbicacion(event.cookies.get(COOKIE_UBIC)) };
};
