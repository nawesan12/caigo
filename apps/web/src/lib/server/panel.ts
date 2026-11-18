import { error, redirect, type RequestEvent } from '@sveltejs/kit';
import { conSesion, cortarSiSuspendida } from './api.ts';
import type { Lugar } from '#lib/tipos.ts';
import { dev } from '$app/env';

/** El lugar elegido en el selector del panel (si la persona maneja varios). */
export const COOKIE_LUGAR = 'caigo_lugar';

export function elegirLugar(event: RequestEvent, id: string) {
	event.cookies.set(COOKIE_LUGAR, id, {
		path: '/',
		httpOnly: true,
		secure: !dev,
		sameSite: 'lax',
		maxAge: 365 * 86400
	});
}

/** Los lugares de la persona y el que está mirando. */
export async function misLugares(event: RequestEvent) {
	let lugares: Lugar[];
	try {
		({ lugares } = await conSesion<{ lugares: Lugar[] }>(event, 'GET', '/v1/lugares'));
	} catch (e) {
		// Suspendida con la cookie viva: a /ingresar con el aviso, como en /app (antes, un 500).
		cortarSiSuspendida(event, e);
		throw e;
	}
	const elegido = event.cookies.get(COOKIE_LUGAR);
	const lugar =
		lugares.find((l) => l.id === elegido) ??
		lugares.find((l) => l.estado === 'aprobado') ??
		lugares[0] ??
		null;
	return { lugares, lugar };
}

/** Para las páginas que necesitan un lugar aprobado: si no, al inicio del panel. */
export async function lugarAprobado(event: RequestEvent & { parent: () => Promise<unknown> }) {
	const { lugar } = (await event.parent()) as { lugar: Lugar | null };
	if (!lugar) redirect(303, '/registro');
	if (lugar.estado !== 'aprobado') redirect(303, '/');
	return lugar;
}

/** Un id con forma de UUID (lo que va en las rutas de la API). */
export function uuid(id: string): string {
	if (!/^[0-9a-f-]{36}$/i.test(id)) error(404, 'No encontrado');
	return id;
}

/**
 * Por qué no se conectó Mercado Pago (la vuelta manda `/cobros?mp=error&detalle=<código>`). Sólo
 * textos fijos: lo que viene en la URL lo arma cualquiera y no se muestra tal cual.
 */
const ERRORES_MP: Record<string, string> = {
	estado: 'El link de conexión venció. Probá de nuevo.',
	mp_en_uso: 'Esa cuenta de Mercado Pago ya está conectada a otro lugar de Caigo.',
	sin_mp: 'Mercado Pago todavía no está disponible en Caigo.'
};

export function errorMP(codigo: string | null | undefined): string {
	return codigo && Object.hasOwn(ERRORES_MP, codigo) ? ERRORES_MP[codigo] : '';
}
