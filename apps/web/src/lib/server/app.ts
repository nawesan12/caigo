import type { RequestEvent } from '@sveltejs/kit';
import { centroDe, COOKIE_UBIC, leerUbicacion } from '#lib/app.ts';
import { enElDia } from '#lib/dia.ts';
import type { Plan } from '#lib/tipos.ts';
import { conSesion } from './api.ts';

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

/**
 * El plan de hoy de quien mira (el próximo en el que está adentro y que está «en el día»): la tarjeta
 * fija de Ahora y Mis planes, con «Llegué» y «¿Todo bien?» sin entrar al plan. `listas`: las de Mis
 * planes, si ya se trajeron.
 */
export async function planDeHoy(event: RequestEvent, listas?: Plan[][]): Promise<Plan | null> {
	if (!event.locals.token) return null;
	const ls =
		listas ??
		(await Promise.all(
			['organizo', 'me_sume'].map((k) =>
				conSesion<{ planes: Plan[] }>(event, 'GET', `/v1/planes/mios?cual=${k}`)
					.then((r) => r.planes)
					.catch(() => [] as Plan[])
			)
		));
	return (
		ls
			.flat()
			.filter((p) => (p.miEstado === 'aceptada' || p.miEstado === 'organizador') && enElDia(p))
			.sort((a, b) => a.empiezaEn.localeCompare(b.empiezaEn))[0] ?? null
	);
}
