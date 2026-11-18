import { error } from '@sveltejs/kit';
import type { Handle, HandleServerError } from '@sveltejs/kit/hooks';
import { esDelPanel, sitioDe } from '#lib/hosts.ts';
import { COOKIE, tokenValido } from '#lib/server/sesion.ts';
import { guardarOrigen } from '#lib/server/origen.ts';
import { guardApp } from '#lib/server/cuenta.ts';
import { ErrorApi } from '#lib/server/api.ts';

export const handle: Handle = async ({ event, resolve }) => {
	event.locals.sitio = sitioDe(event.url.host);
	const t = event.cookies.get(COOKIE);
	event.locals.token = tokenValido(t) ? t : null;
	// El panel sólo se sirve en lugares.*: caigo.com.ar/panel no existe.
	if (event.locals.sitio === 'caigo' && esDelPanel(event.route.id)) error(404, 'No encontrado');
	// /app: la verificación completa (teléfono, selfie y DNI) para todo, también form actions y
	// +server.ts, salvo el alta. Si la sesión venció, sigue sin sesión.
	const corte = await guardApp(event);
	if (corte) return corte;
	// De dónde llegó (?utm_source=, ?ref=, /p/<slug>): sólo quien todavía no tiene cuenta abierta.
	const conOrigen =
		!event.locals.token &&
		event.request.method === 'GET' &&
		guardarOrigen(event.cookies, event.url);
	const res = await resolve(event, {
		// Además de JS y CSS, se precargan las Geist que pinta lo primero (cuerpo 600, títulos 700).
		preload: ({ type, path }) =>
			type === 'js' ||
			type === 'css' ||
			(type === 'font' && /geist-latin-(600|700)-normal[^/]*\.woff2$/.test(path))
	});
	// Una respuesta que deja una cookie no se guarda en cachés compartidas (/p/<slug> es pública).
	if (conOrigen) res.headers.set('cache-control', 'private, no-store');
	res.headers.set('X-Content-Type-Options', 'nosniff');
	res.headers.set('Referrer-Policy', 'strict-origin-when-cross-origin');
	if (!res.headers.has('X-Frame-Options')) res.headers.set('X-Frame-Options', 'DENY');
	return res;
};

/**
 * Un error inesperado se muestra en +error.svelte. Si es que la API no contestó, con su mensaje («No
 * pudimos hablar con Caigo…») en vez del «Internal Error» genérico.
 */
export const handleError: HandleServerError = ({ kind, error }) => {
	if (kind !== 'unknown') return;
	if (error instanceof ErrorApi && error.estado >= 500)
		return { status: 503, message: error.detalle };
	console.error(error);
};
