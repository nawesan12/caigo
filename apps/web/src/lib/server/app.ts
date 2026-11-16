import type { RequestEvent } from '@sveltejs/kit';
import { centroDe, COOKIE_UBIC, leerUbicacion } from '#lib/app.ts';

/**
 * La ubicación de quien mira (cookie que pone el navegador) o, sin eso, el centro de su ciudad
 * (`ciudad` del perfil: Villa Gesell no ve el feed de Mar del Plata).
 */
export function ubicacion(
	event: RequestEvent,
	ciudad?: string | null
): { lat: number; lng: number; real: boolean } {
	const u = leerUbicacion(event.cookies.get(COOKIE_UBIC));
	const [lat, lng] = u ?? centroDe(ciudad);
	return { lat, lng, real: !!u };
}

/** ?lat=…&lng=… para las listas de la API. */
export function conUbicacion(
	event: RequestEvent,
	extra: Record<string, string> = {},
	ciudad?: string | null
): string {
	const { lat, lng } = ubicacion(event, ciudad);
	const q = new URLSearchParams({ lat: String(lat), lng: String(lng) });
	for (const [k, v] of Object.entries(extra)) if (v) q.set(k, v);
	return q.toString();
}
