import { error } from '@sveltejs/kit';
import { baseApi } from '#lib/server/api.ts';
import type { RequestHandler } from './$types';

/**
 * Tiempo real para la web: el navegador abre un EventSource acá y el servidor se conecta al
 * WebSocket de la API (/v1/tiempo-real) con la sesión de la cookie, que nunca va al navegador.
 * Cada evento (mensaje, privado, escribiendo, plan) pasa tal cual. Si algo se corta, el stream se
 * cierra y el EventSource se reconecta solo.
 */
export const GET: RequestHandler = ({ locals, request }) => {
	if (!locals.token) error(401, 'Ingresá para seguir.');
	const url = `${baseApi().replace(/^http/, 'ws')}/v1/tiempo-real?token=${encodeURIComponent(locals.token)}`;
	const texto = new TextEncoder();
	let ws: WebSocket | undefined;
	let latido: ReturnType<typeof setInterval> | undefined;
	const cerrar = () => {
		clearInterval(latido);
		try {
			ws?.close();
		} catch {
			/* ya cerrado */
		}
	};
	const stream = new ReadableStream<Uint8Array>({
		start(c) {
			const mandar = (s: string) => {
				try {
					c.enqueue(texto.encode(s));
				} catch {
					cerrar();
				}
			};
			const terminar = () => {
				cerrar();
				try {
					c.close();
				} catch {
					/* ya cerrado */
				}
			};
			ws = new WebSocket(url);
			ws.onmessage = (e) => mandar(`data: ${String(e.data)}\n\n`);
			ws.onclose = terminar;
			ws.onerror = terminar;
			// Cada 25 s un comentario, para que ningún proxy corte la conexión por inactiva.
			latido = setInterval(() => mandar(': latido\n\n'), 25_000);
			mandar('retry: 3000\n\n');
			request.signal.addEventListener('abort', terminar);
		},
		cancel: cerrar
	});
	return new Response(stream, {
		headers: {
			'content-type': 'text/event-stream; charset=utf-8',
			'cache-control': 'no-cache, no-transform',
			'x-accel-buffering': 'no'
		}
	});
};
