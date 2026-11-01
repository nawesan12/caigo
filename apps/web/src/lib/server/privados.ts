import { fail, redirect, type RequestEvent } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { uuid } from '#lib/server/panel.ts';
import type { Bloqueado, Conversacion } from '#lib/tipos.ts';

/** Cuántos privados devuelve la API por página (chat.Hilo): con 100, puede haber más atrás. */
export const PAGINA = 100;

/**
 * Las conversaciones (GET /v1/privados) sin las de gente bloqueada: la API las sigue listando,
 * pero su chat ya no se puede abrir (da 404).
 */
export async function conversaciones(event: RequestEvent): Promise<Conversacion[]> {
	const [r, b] = await Promise.all([
		conSesion<{ conversaciones: Conversacion[] }>(event, 'GET', '/v1/privados'),
		conSesion<{ bloqueos: Bloqueado[] }>(event, 'GET', '/v1/bloqueos').catch((e) => {
			if (e instanceof ErrorApi) return { bloqueos: [] as Bloqueado[] };
			throw e;
		})
	]);
	const fuera = new Set((b?.bloqueos ?? []).map((x) => x.id));
	return r.conversaciones.filter((c) => !fuera.has(c.con.id));
}

/** Los no leídos de todas las conversaciones, para el contador del acceso a Mensajes. */
export async function sinLeer(event: RequestEvent): Promise<number> {
	try {
		return (await conversaciones(event)).reduce((n, c) => n + c.sinLeer, 0);
	} catch (e) {
		if (e instanceof ErrorApi) return 0;
		throw e;
	}
}

/**
 * Bloquear a alguien (PUT /v1/bloqueos/{id}) desde el chat o su perfil: deja de ver y de ser visto,
 * y vuelve a la bandeja con el toast de las apps («Bloqueaste a Lu. No se van a ver más.»).
 */
export async function bloquear(event: RequestEvent, id: string) {
	uuid(id);
	const nombre = String((await event.request.formData()).get('nombre') ?? '').slice(0, 40);
	try {
		await conSesion(event, 'PUT', `/v1/bloqueos/${id}`);
	} catch (e) {
		if (!(e instanceof ErrorApi)) throw e;
		return fail(400, { error: mensajeDe(e) });
	}
	redirect(303, `/app/mensajes?bloqueaste=${encodeURIComponent(nombre)}`);
}
