import { json, redirect, type RequestEvent } from '@sveltejs/kit';
import { api, cortarSiSuspendida, esDePagina, ErrorApi, ipDe } from './api.ts';
import { borrarSesion } from './sesion.ts';
import { motivoDni, verificada } from '#lib/verificacion.ts';
import type { Verificacion, Yo } from '#lib/tipos.ts';

/**
 * El control de la verificación de /app, en un solo lugar (hooks.server.ts): el DNI aprobado para
 * todo lo que no sea mirar (soloMira). No alcanza con el `load` del layout: las form actions y los +server.ts de /app no lo
 * corren, y sin esto se podía chatear, crear o subir fotos sin verificarse.
 */

/** El id de la selfie de la prueba de vida, para mandarlo con el DNI (httpOnly, sólo /app/verificar). */
export const COOKIE_SELFIE = 'caigo_selfie';

/** Lo que se puede usar sin la verificación completa (el alta y lo que la rodea). */
const SIN_VERIFICAR = [
	'/app/verificar',
	'/app/intereses',
	'/app/perfil/editar',
	// La foto del perfil se sube desde «Completá tu perfil» (/app/perfil/editar).
	'/app/perfil/foto',
	'/app/borrar'
];

/** Lo que se mira sin cuenta (layout de /app): sin sesión no se controla nada. */
export const SIN_CUENTA = new Set([
	'/app',
	'/app/mapa',
	'/app/explorar',
	'/app/plan/[id]',
	'/app/bienvenida',
	// Redirige a /borrar-cuenta (pública).
	'/app/borrar'
]);

/**
 * Lo que se mira con cuenta y sin el DNI (18/11, «Mirar sin DNI»): Ahora, el mapa, Explorar, el
 * detalle, los perfiles, Mis planes y el perfil propio. Sólo para mirar (GET): sumarse, crear o
 * chatear piden el DNI, y la API también lo exige (403 `falta_dni`).
 */
const MIRAR = new Set([...SIN_CUENTA, '/app/persona/[id]', '/app/perfil', '/app/mis']);

export function soloMira(event: RequestEvent): boolean {
	const m = event.request.method;
	return (
		MIRAR.has(event.route.id ?? '') &&
		(m === 'GET' || m === 'HEAD') &&
		!event.request.headers.get('x-sveltekit-action')
	);
}

export function exentaDeVerificar(rutaId: string): boolean {
	return SIN_VERIFICAR.some((r) => rutaId === r || rutaId.startsWith(r + '/'));
}

/**
 * /app/verificar y de vuelta a donde estaba (las form actions vuelven a la página). Con el motivo
 * (sumarte, crear, chatear) para el título de la primera lámina.
 */
export function aVerificar(event: RequestEvent): never {
	const volver =
		event.request.method === 'GET' ? event.url.pathname + event.url.search : event.url.pathname;
	const motivo = motivoDni(event.route.id ?? '');
	redirect(
		303,
		`/app/verificar?volver=${encodeURIComponent(volver)}${motivo ? `&motivo=${motivo}` : ''}`
	);
}

/**
 * La verificación de quien pide, si tiene sesión. Si la sesión venció (401) borra la cookie y sigue
 * sin sesión: lo público se ve igual y lo demás pide ingresar.
 */
export async function leerVerificacion(event: RequestEvent): Promise<Verificacion | null> {
	const token = event.locals.token;
	if (!token) return null;
	try {
		return await api<Verificacion>('GET', '/v1/verificacion', { token, ip: ipDe(event) });
	} catch (e) {
		if (e instanceof ErrorApi && e.estado === 401) {
			borrarSesion(event.cookies);
			event.locals.token = null;
			return null;
		}
		cortarSiSuspendida(event, e);
		throw e;
	}
}

/**
 * El guard de /app (lo llama hooks.server.ts antes de resolver). Deja la verificación en
 * `locals.verif` para que el layout no la vuelva a pedir. Devuelve una respuesta si corta el pedido.
 */
export async function guardApp(event: RequestEvent): Promise<Response | null> {
	const ruta = event.route.id ?? '';
	if (!ruta.startsWith('/app')) return null;
	try {
		event.locals.verif = await leerVerificacion(event);
	} catch (e) {
		if (e instanceof ErrorApi && e.estado === 403)
			return json({ error: e.detalle, code: 'suspendida' }, { status: 403 });
		// La API no contesta (caída, reiniciando): un error acá salía como la página cruda «500 Internal
		// Error» de SvelteKit (lo que falla en hooks no pasa por +error.svelte). Una página sigue y su
		// layout, que vuelve a pedir la verificación, muestra el error de Caigo; el resto, un JSON.
		if (e instanceof ErrorApi && e.estado >= 500) {
			if (esDePagina(event)) return null;
			return json({ error: e.detalle, code: 'sin_api' }, { status: 503 });
		}
		throw e;
	}
	const v = event.locals.verif;
	if (!v || verificada(v) || exentaDeVerificar(ruta) || soloMira(event)) return null;
	if (esDePagina(event)) aVerificar(event);
	return json(
		{ error: 'Primero terminá de verificar tu cuenta.', code: 'falta_verificacion' },
		{ status: 403 }
	);
}

/**
 * Para las páginas fuera de /app que necesitan la cuenta verificada (Plus): si falta algo, a
 * /app/verificar y de vuelta a `volver`.
 */
export async function exigirVerificada(event: RequestEvent, volver: string): Promise<void> {
	const v = await leerVerificacion(event);
	if (v && !verificada(v)) redirect(303, `/app/verificar?volver=${encodeURIComponent(volver)}`);
}

/**
 * Para lo público con sesión opcional (/p/<slug>, /plus): si la cookie venció, la borra y sigue
 * como anónimo en vez de mandar a ingresar.
 */
export async function sesionViva(event: RequestEvent): Promise<Yo | null> {
	const token = event.locals.token;
	if (!token) return null;
	try {
		return await api<Yo>('GET', '/v1/yo', { token, ip: ipDe(event) });
	} catch (e) {
		if (e instanceof ErrorApi && e.estado === 401) {
			borrarSesion(event.cookies);
			event.locals.token = null;
			return null;
		}
		// Suspendida con la cookie viva: como en /app, a /ingresar con el aviso (antes, un 500).
		cortarSiSuspendida(event, e);
		throw e;
	}
}
