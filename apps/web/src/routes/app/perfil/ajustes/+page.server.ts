import { dev } from '$app/env';
import { fail, type RequestEvent } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { INTENCIONES } from '#lib/app.ts';
import type { Actions } from './$types';

const GENEROS = ['', 'mujer', 'varon', 'no_binario'];

/** PATCH /v1/yo con lo que cambió; los errores vuelven como { error } para el toast. */
async function guardar(event: RequestEvent, cuerpo: Record<string, unknown>, ok: object) {
	try {
		await conSesion(event, 'PATCH', '/v1/yo', { cuerpo });
		return ok;
	} catch (e) {
		if (!(e instanceof ErrorApi)) throw e;
		// La API vieja rechaza los campos que todavía no conoce (genero, avisos…).
		if (e.estado === 400 && /unknown field/i.test(e.detalle))
			return fail(400, { error: 'Todavía no podemos guardar esto. Probá en un rato.' });
		return fail(400, { error: mensajeDe(e) });
	}
}

export const actions: Actions = {
	genero: async (event) => {
		const f = await event.request.formData();
		const genero = String(f.get('genero') ?? '');
		if (!GENEROS.includes(genero)) return fail(400, { error: 'Elegí una opción.' });
		const diversidad = f.get('diversidad') === '1';
		return guardar(event, { genero, diversidad }, { genero, diversidad });
	},
	intenciones: async (event) => {
		const f = await event.request.formData();
		const validas = new Set(INTENCIONES.map((i) => i.valor));
		const intenciones = f
			.getAll('intencion')
			.map(String)
			.filter((i) => validas.has(i));
		if (!intenciones.length) return fail(400, { error: 'Elegí al menos una.' });
		return guardar(event, { intenciones }, { intenciones });
	},
	contacto: async (event) => {
		const contactoConfianza = String((await event.request.formData()).get('contacto') ?? '')
			.trim()
			.slice(0, 80);
		return guardar(event, { contactoConfianza }, { contactoConfianza });
	},
	avisos: async (event) => {
		const f = await event.request.formData();
		const avisos = { planesCerca: f.get('pc') === '1', tuGente: f.get('tg') === '1' };
		if (f.get('cambia') === 'pc') avisos.planesCerca = !avisos.planesCerca;
		if (f.get('cambia') === 'tg') avisos.tuGente = !avisos.tuGente;
		return guardar(event, { avisos }, { avisos });
	},
	tema: async ({ request, cookies }) => {
		const t = String((await request.formData()).get('tema') ?? '');
		if (!['claro', 'oscuro', 'sistema'].includes(t)) return fail(400, { error: 'Elegí un tema.' });
		cookies.set('caigo_tema', t, {
			path: '/',
			httpOnly: false,
			secure: !dev,
			sameSite: 'lax',
			maxAge: 365 * 86400
		});
		return { tema: t };
	}
};
