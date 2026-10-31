/**
 * Cómo se entregan las OG: ETag = la versión; con la versión que corresponde en ?v=, un año
 * inmutable; sin ?v= o con una vieja, cinco minutos (los datos del plan pueden haber cambiado).
 * Un caché chico en memoria evita volver a dibujar la misma imagen (cada una tarda ~200 ms).
 */

import { dev } from '$app/env';

const MAX = 48;
const cache = new Map<string, Uint8Array>();

/** Busca en el caché o dibuja (y guarda, sacando la más vieja si se llenó). */
export async function memo(clave: string, dibujar: () => Promise<Uint8Array>): Promise<Uint8Array> {
	if (dev) return dibujar(); // en desarrollo, siempre el diseño recién guardado
	const hay = cache.get(clave);
	if (hay) {
		cache.delete(clave);
		cache.set(clave, hay);
		return hay;
	}
	const png = await dibujar();
	cache.set(clave, png);
	if (cache.size > MAX) cache.delete(cache.keys().next().value as string);
	return png;
}

export function cacheControl(pedida: string | null, version: string): string {
	return pedida === version
		? 'public, max-age=31536000, immutable'
		: 'public, max-age=300, stale-while-revalidate=3600';
}

/** 304 si el navegador o la CDN ya la tienen. */
export function noCambio(request: Request, etag: string, cc: string): Response | null {
	const inm = request.headers.get('if-none-match');
	if (!inm) return null;
	const pedidas = inm.split(',').map((s) => s.trim().replace(/^W\//, ''));
	if (!pedidas.includes(etag) && !pedidas.includes('*')) return null;
	return new Response(null, { status: 304, headers: { etag, 'cache-control': cc } });
}

export function respuestaPng(png: Uint8Array, etag: string, cc: string): Response {
	return new Response(png as BodyInit, {
		headers: {
			'content-type': 'image/png',
			'content-length': String(png.byteLength),
			'cache-control': cc,
			etag
		}
	});
}
