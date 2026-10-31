import type { RequestEvent } from '@sveltejs/kit';
import { CENTRO_MDP, COOKIE_UBIC, leerUbicacion } from '#lib/app.ts';

/** La ubicación de quien mira (cookie que pone el navegador) o el centro de Mar del Plata. */
export function ubicacion(event: RequestEvent): { lat: number; lng: number; real: boolean } {
	const u = leerUbicacion(event.cookies.get(COOKIE_UBIC));
	const [lat, lng] = u ?? CENTRO_MDP;
	return { lat, lng, real: !!u };
}

/** ?lat=…&lng=… para las listas de la API. */
export function conUbicacion(event: RequestEvent, extra: Record<string, string> = {}): string {
	const { lat, lng } = ubicacion(event);
	const q = new URLSearchParams({ lat: String(lat), lng: String(lng) });
	for (const [k, v] of Object.entries(extra)) if (v) q.set(k, v);
	return q.toString();
}
