import { CAIGO_API } from '$app/env/private';
import { redirect, type RequestEvent } from '@sveltejs/kit';
import { volverSeguro } from '#lib/hosts.ts';

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
};

/** Llama a la API y devuelve el JSON (o null en 204). Tira ErrorApi si no salió bien. */
export async function api<T = unknown>(
	metodo: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE',
	ruta: string,
	o: Opciones = {}
): Promise<T> {
	const headers: Record<string, string> = { accept: 'application/json' };
	if (o.token) headers.authorization = `Bearer ${o.token}`;
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

/** Como `api`, con la sesión de la cookie. Si la sesión venció (401), borra la cookie y manda a ingresar. */
export async function conSesion<T = unknown>(
	event: RequestEvent,
	metodo: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE',
	ruta: string,
	o: Omit<Opciones, 'token'> = {}
): Promise<T> {
	const token = exigirToken(event);
	try {
		return await api<T>(metodo, ruta, { ...o, token });
	} catch (e) {
		if (e instanceof ErrorApi && e.estado === 401) {
			event.cookies.delete('caigo_sesion', { path: '/' });
			event.locals.token = null;
			aIngresar(event);
		}
		throw e;
	}
}
