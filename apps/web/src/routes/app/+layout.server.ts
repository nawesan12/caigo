import { redirect } from '@sveltejs/kit';
import { conSesion } from '#lib/server/api.ts';
import { aVerificar, exentaDeVerificar, SIN_CUENTA } from '#lib/server/cuenta.ts';
import { verificada } from '#lib/verificacion.ts';
import { COOKIE_UBIC, leerUbicacion } from '#lib/app.ts';
import type { Verificacion, Yo } from '#lib/tipos.ts';
import type { LayoutServerLoad } from './$types';
import { usarInvitacion } from '#lib/server/invitacion.ts';

/** Tema de /app (perfil › ajustes): claro, oscuro o como el teléfono. */
const TEMAS = ['claro', 'oscuro', 'sistema'] as const;
type Tema = (typeof TEMAS)[number];

/** Onboarding (01a–01c y "¿Qué buscás?"): cookies de "ya lo vio". */
const COOKIE_BIENVENIDA = 'caigo_bienvenida';
const COOKIE_INTERESES = 'caigo_intereses';
const AL_PASO = new Set(['/app/verificar', '/app/borrar', '/app/bienvenida', '/app/intereses']);

/**
 * Caigo desde la web. Sin cuenta se pueden mirar los planes; para sumarse, crear o chatear pide
 * sesión (Google o Apple) y, una sola vez, la verificación: teléfono, selfie y DNI. El control de
 * verdad está en hooks.server.ts (lib/server/cuenta.ts), que cubre también las form actions y los
 * +server.ts; acá sólo se arma el orden del alta.
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
		event.locals.verif ?? conSesion<Verificacion>(event, 'GET', '/v1/verificacion'),
		usarInvitacion(event)
	]);
	// Sin la verificación completa, sólo el alta (hooks.server.ts ya cortó lo demás).
	if (!verificada(verif) && !exentaDeVerificar(ruta)) aVerificar(event);
	// Mientras se verifica no se le piden intereses ni perfil: primero termina la verificación.
	if (!verificada(verif)) {
		event.setHeaders({ 'cache-control': 'private, no-store' });
		return { yo, verif, anonimo: false, tema, ubic: leerUbicacion(event.cookies.get(COOKIE_UBIC)) };
	}
	// Ya verificada, una vez: qué busca y qué le copa (intenciones e intereses).
	const intenciones = (yo as Yo & { intenciones?: string[] }).intenciones ?? [];
	if (!intenciones.length && !AL_PASO.has(ruta) && !event.cookies.get(COOKIE_INTERESES)) {
		const volver = event.url.pathname + event.url.search;
		redirect(303, `/app/intereses?volver=${encodeURIComponent(volver)}`);
	}
	// Y antes de usar la app, el perfil completo (personas.Faltantes en la API): sin perfiles vacíos.
	const faltan = (yo as Yo & { faltan?: string[] }).faltan ?? [];
	if (faltan.length && !AL_PASO.has(ruta) && ruta !== '/app/perfil/editar') {
		const volver = event.url.pathname + event.url.search;
		redirect(303, `/app/perfil/editar?completar&volver=${encodeURIComponent(volver)}`);
	}
	event.setHeaders({ 'cache-control': 'private, no-store' });
	return { yo, verif, anonimo: false, tema, ubic: leerUbicacion(event.cookies.get(COOKIE_UBIC)) };
};
