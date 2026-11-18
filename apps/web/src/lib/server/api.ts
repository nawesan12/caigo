import { isIP } from 'node:net';
import { CAIGO_API } from '$app/env/private';
import { redirect, type RequestEvent } from '@sveltejs/kit';
import { volverSeguro } from '#lib/hosts.ts';
import { planPublico } from './publico.ts';
import { borrarSesion } from './sesion.ts';
import type { Plan } from '#lib/tipos.ts';

/**
 * Cliente de la API de Caigo (Go). Todas las llamadas salen del servidor de SvelteKit: el token de
 * la sesión (`cs_…`) vive en una cookie httpOnly y nunca llega al JS del navegador.
 */

export function baseApi(): string {
	return CAIGO_API.replace(/\/+$/, '');
}

/** Un error de la API (problem+json). `detalle` ya está escrito para personas: se muestra tal cual. */
export class ErrorApi extends Error {
	constructor(
		public estado: number,
		public detalle: string,
		public codigo = '',
		public campos: { path: string; message: string }[] = []
	) {
		super(detalle);
	}
}

const SIN_RESPUESTA = 'No pudimos hablar con Caigo. Probá de nuevo en un rato.';

type Opciones = {
	token?: string | null;
	cuerpo?: unknown;
	/** Cuerpo crudo (las fotos de /v1/archivos). */
	crudo?: BodyInit;
	tipo?: string;
	f?: typeof fetch;
	/** IP de quien pidió (la API pone topes por IP y, si no, vería siempre la del servidor web). */
	ip?: string | null;
};

/** Para `conSesion` desde un +server.ts que contesta JSON a un fetch del navegador. */
type OpcionesSesion = Omit<Opciones, 'token'> & {
	/**
	 * Sin sesión o sin DNI tira ErrorApi (401/403) en vez de redirigir: el fetch seguía el 303 y
	 * recibía el HTML de /ingresar con 200, que la página tomaba por un mensaje.
	 */
	json?: boolean;
};

const SESION_VENCIDA = 'Tu sesión venció. Volvé a ingresar.';

/**
 * La IP real de quien pide: Cloudflare la manda en Cf-Connecting-Ip y Caddy la deja pasar. Sólo si
 * es una IP: se reenvía a la API, que la usa para los topes y la anota en los registros.
 */
export function ipDe(event: RequestEvent): string | null {
	const cf = event.request.headers.get('cf-connecting-ip')?.trim();
	if (cf && isIP(cf)) return cf;
	try {
		const c = event.getClientAddress();
		return isIP(c) ? c : null;
	} catch {
		return null;
	}
}

/** Llama a la API y devuelve el JSON (o null en 204). Tira ErrorApi si no salió bien. */
export async function api<T = unknown>(
	metodo: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE',
	ruta: string,
	o: Opciones = {}
): Promise<T> {
	const headers: Record<string, string> = { accept: 'application/json' };
	if (o.token) headers.authorization = `Bearer ${o.token}`;
	if (o.ip) headers['cf-connecting-ip'] = o.ip;
	let body: BodyInit | undefined;
	if (o.crudo !== undefined) {
		body = o.crudo;
		headers['content-type'] = o.tipo || 'application/octet-stream';
	} else if (o.cuerpo !== undefined) {
		body = JSON.stringify(o.cuerpo);
		headers['content-type'] = 'application/json';
	}
	let res: Response;
	try {
		res = await (o.f ?? fetch)(baseApi() + ruta, { method: metodo, headers, body });
	} catch {
		throw new ErrorApi(503, SIN_RESPUESTA, 'sin_api');
	}
	if (res.status === 204) return null as T;
	const texto = await res.text();
	let json: unknown;
	try {
		json = texto ? JSON.parse(texto) : null;
	} catch {
		json = null;
	}
	if (!res.ok) {
		const p = (json ?? {}) as {
			detail?: string;
			title?: string;
			code?: string;
			issues?: { path: string; message: string }[];
		};
		throw new ErrorApi(
			res.status,
			p.detail || p.title || SIN_RESPUESTA,
			p.code ?? '',
			p.issues ?? []
		);
	}
	return json as T;
}

/** El mensaje para mostrar de un error cualquiera (el `detail` de la API si vino de ahí). */
export function mensajeDe(e: unknown): string {
	if (e instanceof ErrorApi) {
		// Con campos, el primero dice qué falta ("Poné el nombre del plan.").
		if (e.campos.length && e.estado === 400) return e.campos[0].message || e.detalle;
		return e.detalle;
	}
	return SIN_RESPUESTA;
}

/** A /ingresar y de vuelta acá. */
export function aIngresar(event: RequestEvent, volver?: string): never {
	const v = volverSeguro(volver ?? event.url.pathname + event.url.search);
	redirect(303, `/ingresar?volver=${encodeURIComponent(v)}`);
}

/** El token de la sesión; si no hay, manda a ingresar. */
export function exigirToken(event: RequestEvent): string {
	const t = event.locals.token;
	if (!t) aIngresar(event);
	return t;
}

/** Sin sesión, de cada plan (suelto o en `planes`) sólo el primer nombre y la foto de la gente. */
function sinDatosPersonales<T>(r: T): T {
	const esPlan = (x: unknown): x is Plan =>
		!!x && typeof x === 'object' && 'organizador' in x && 'van' in x;
	if (esPlan(r)) return planPublico(r) as T;
	if (r && typeof r === 'object' && 'planes' in r && Array.isArray(r.planes))
		return { ...r, planes: r.planes.map((p: unknown) => (esPlan(p) ? planPublico(p) : p)) };
	return r;
}

/**
 * Para mirar sin cuenta: con sesión llama a `ruta`; sin sesión, a `publica` (la versión de
 * /v1/publico/..., que sólo muestra planes públicos). Lo que convierte es dejar ver antes de pedir
 * ingresar.
 */
export async function quizasConSesion<T = unknown>(
	event: RequestEvent,
	ruta: string,
	publica: string
): Promise<T> {
	if (event.locals.token) return conSesion<T>(event, 'GET', ruta);
	return sinDatosPersonales(await api<T>('GET', publica, { ip: ipDe(event) }));
}

/**
 * Las listas de planes de /app, el mapa y explorar. Un 4xx de la API (un 429 de los topes, un
 * filtro que no le cierra) no rompe la pantalla con un 500: lista vacía y el porqué en `aviso`. El
 * 403 `plus` sigue de largo (explorar lo manda a /plus).
 */
export async function planesQuizas(
	event: RequestEvent,
	ruta: string,
	publica: string
): Promise<{ planes: Plan[]; aviso: string }> {
	try {
		const r = await quizasConSesion<{ planes?: Plan[] }>(event, ruta, publica);
		return { planes: r?.planes ?? [], aviso: '' };
	} catch (e) {
		if (e instanceof ErrorApi && e.estado >= 400 && e.estado < 500 && e.codigo !== 'plus')
			return { planes: [], aviso: mensajeDe(e) };
		throw e;
	}
}

/** Si el pedido es de una página (GET, datos o una form action) y no de un +server.ts. */
export function esDePagina(event: RequestEvent): boolean {
	const m = event.request.method;
	if (m === 'GET' || m === 'HEAD') return true;
	if (event.request.headers.get('x-sveltekit-action')) return true;
	const tipo = event.request.headers.get('content-type') ?? '';
	return tipo.startsWith('application/x-www-form-urlencoded') || tipo.startsWith('multipart/');
}

/**
 * Como `api`, con la sesión de la cookie. Si la sesión venció (401), borra la cookie y manda a
 * ingresar; si a la cuenta le falta el DNI (403 `falta_dni`), una página va a /app/verificar.
 */
export async function conSesion<T = unknown>(
	event: RequestEvent,
	metodo: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE',
	ruta: string,
	o: OpcionesSesion = {}
): Promise<T> {
	const { json, ...resto } = o;
	if (json && !event.locals.token) throw new ErrorApi(401, SESION_VENCIDA, 'sin_sesion');
	const token = exigirToken(event);
	try {
		return await api<T>(metodo, ruta, { ...resto, token, ip: ipDe(event) });
	} catch (e) {
		if (e instanceof ErrorApi && e.estado === 401) {
			event.cookies.delete('caigo_sesion', { path: '/' });
			event.locals.token = null;
			if (json) throw new ErrorApi(401, SESION_VENCIDA, 'sin_sesion');
			aIngresar(event);
		}
		if (e instanceof ErrorApi && e.codigo === 'falta_dni' && !json && esDePagina(event)) {
			const v =
				event.request.method === 'GET' ? event.url.pathname + event.url.search : event.url.pathname;
			redirect(303, `/app/verificar?volver=${encodeURIComponent(volverSeguro(v, '/app'))}`);
		}
		throw e;
	}
}

/**
 * Cuenta suspendida (p. ej. el equipo marcó el DNI como trucho): la API contesta 403 a todo. Sólo
 * para lo que no tiene otro 403 posible (yo, verificación, la lista de lugares): cierra la sesión
 * y una página va a /ingresar con el aviso. Si no era eso, no hace nada y el error sigue su curso.
 */
export function cortarSiSuspendida(event: RequestEvent, e: unknown): void {
	if (!(e instanceof ErrorApi) || e.estado !== 403 || e.codigo === 'falta_dni') return;
	borrarSesion(event.cookies);
	event.locals.token = null;
	if (esDePagina(event)) redirect(303, '/ingresar?error=suspendida');
}
