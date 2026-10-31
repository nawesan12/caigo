import { redirect } from '@sveltejs/kit';
import { api } from '#lib/server/api.ts';
import { borrarSesion } from '#lib/server/sesion.ts';
import type { RequestHandler } from './$types';

/** Cierra la sesión en la API (si se puede) y borra la cookie. Sólo por POST (un form). */
export const POST: RequestHandler = async ({ locals, cookies }) => {
	if (locals.token) await api('DELETE', '/v1/sesion', { token: locals.token }).catch(() => null);
	borrarSesion(cookies);
	redirect(303, '/');
};
