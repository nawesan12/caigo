import { error } from '@sveltejs/kit';
import type { Handle } from '@sveltejs/kit/hooks';
import { esDelPanel, sitioDe } from '#lib/hosts.ts';
import { COOKIE, tokenValido } from '#lib/server/sesion.ts';

export const handle: Handle = async ({ event, resolve }) => {
	event.locals.sitio = sitioDe(event.url.host);
	const t = event.cookies.get(COOKIE);
	event.locals.token = tokenValido(t) ? t : null;
	// El panel sólo se sirve en lugares.*: caigo.com.ar/panel no existe.
	if (event.locals.sitio === 'caigo' && esDelPanel(event.route.id)) error(404, 'No encontrado');
	const res = await resolve(event);
	res.headers.set('X-Content-Type-Options', 'nosniff');
	res.headers.set('Referrer-Policy', 'strict-origin-when-cross-origin');
	if (!res.headers.has('X-Frame-Options')) res.headers.set('X-Frame-Options', 'DENY');
	return res;
};
