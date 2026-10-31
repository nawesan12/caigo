import { error } from '@sveltejs/kit';
import type { Handle } from '@sveltejs/kit/hooks';
import { esDelPanel, sitioDe } from '#lib/hosts.ts';
import { COOKIE, tokenValido } from '#lib/server/sesion.ts';
import { guardarOrigen } from '#lib/server/origen.ts';

export const handle: Handle = async ({ event, resolve }) => {
	event.locals.sitio = sitioDe(event.url.host);
	const t = event.cookies.get(COOKIE);
	event.locals.token = tokenValido(t) ? t : null;
	// El panel sólo se sirve en lugares.*: caigo.com.ar/panel no existe.
	if (event.locals.sitio === 'caigo' && esDelPanel(event.route.id)) error(404, 'No encontrado');
	// De dónde llegó (?utm_source=, ?ref=, /p/<slug>): sólo quien todavía no tiene cuenta abierta.
	const conOrigen =
		!event.locals.token &&
		event.request.method === 'GET' &&
		guardarOrigen(event.cookies, event.url);
	const res = await resolve(event);
	// Una respuesta que deja una cookie no se guarda en cachés compartidas (/p/<slug> es pública).
	if (conOrigen) res.headers.set('cache-control', 'private, no-store');
	res.headers.set('X-Content-Type-Options', 'nosniff');
	res.headers.set('Referrer-Policy', 'strict-origin-when-cross-origin');
	if (!res.headers.has('X-Frame-Options')) res.headers.set('X-Frame-Options', 'DENY');
	return res;
};
