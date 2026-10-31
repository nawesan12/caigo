import { error, json } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import type { RequestHandler } from './$types';

const MOTIVOS = ['acoso', 'falso', 'no_vino', 'inapropiado', 'inseguro', 'otro'];
const UUID = /^[0-9a-f-]{36}$/i;

/** Reportar un plan o a una persona (POST /v1/reportes). Lo usa la hoja «Reportar» de /app. */
export const POST: RequestHandler = async (event) => {
	// JSON por fetch (no un formulario): el origen se chequea acá.
	if (event.request.headers.get('origin') !== event.url.origin) error(403, 'Origen no permitido.');
	const b = (await event.request.json().catch(() => ({}))) as Record<string, unknown>;
	const motivo = String(b.motivo ?? '');
	const planId = String(b.planId ?? '');
	const usuarioId = String(b.usuarioId ?? '');
	if (!MOTIVOS.includes(motivo)) return json({ error: 'Elegí un motivo.' }, { status: 400 });
	if (!UUID.test(planId) && !UUID.test(usuarioId))
		return json({ error: 'Decí qué reportás.' }, { status: 400 });
	try {
		await conSesion(event, 'POST', '/v1/reportes', {
			cuerpo: {
				motivo,
				planId: UUID.test(planId) ? planId : '',
				usuarioId: UUID.test(usuarioId) ? usuarioId : '',
				detalle: String(b.detalle ?? '')
					.trim()
					.slice(0, 1000)
			}
		});
		return json({ ok: true });
	} catch (e) {
		if (!(e instanceof ErrorApi)) throw e;
		return json({ error: mensajeDe(e) }, { status: e.estado });
	}
};
