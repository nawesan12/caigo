import { redirect } from '@sveltejs/kit';
import { guardarInvitacion } from '#lib/server/invitacion.ts';
import type { RequestHandler } from './$types';

/**
 * caigo.com.ar/i/<código>: el link de «Traé a alguien». Guarda el código y lleva a Caigo; cuando la
 * persona entra, /app lo manda a la API (lib/server/invitacion.ts).
 */
export const GET: RequestHandler = (event) => {
	const codigo = event.params.codigo.toLowerCase();
	if (/^[a-z2-9]{8}$/.test(codigo)) guardarInvitacion(event, codigo);
	redirect(303, '/app');
};
