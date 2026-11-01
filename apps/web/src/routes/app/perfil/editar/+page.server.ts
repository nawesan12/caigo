import { fail, redirect } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import type { Prompt, YoCompleto } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

const texto = (v: unknown, n: number) =>
	String(v ?? '')
		.trim()
		.slice(0, n);

/** Una foto subida por /app/perfil/foto (la URL pública de /v1/archivos/<id>). */
const esFoto = (u: unknown): u is string =>
	typeof u === 'string' && /^https?:\/\/[^\s]+\/v1\/archivos\/[0-9a-f-]{36}$/i.test(u);

export const load: PageServerLoad = ({ url }) => ({ completar: url.searchParams.has('completar') });

export const actions: Actions = {
	default: async (event) => {
		let d: Record<string, unknown>;
		try {
			d = JSON.parse(String((await event.request.formData()).get('datos') ?? '{}'));
		} catch {
			return fail(400, { error: 'Revisá tu perfil.' });
		}
		const yo = await conSesion<YoCompleto>(event, 'GET', '/v1/yo');
		const fotos = (Array.isArray(d.fotos) ? d.fotos : [])
			.filter((f) => yo.fotos.includes(f as string) || esFoto(f))
			.slice(0, 6) as string[];
		const prompts: Prompt[] = (Array.isArray(d.prompts) ? d.prompts : [])
			.map((x) => ({ p: texto(x?.p, 60), r: texto(x?.r, 140) }))
			.filter((x) => x.p && x.r)
			.slice(0, 3);
		const intereses = (Array.isArray(d.intereses) ? d.intereses : [])
			.map((x) => texto(x, 30))
			.filter(Boolean)
			.slice(0, 12);
		const ciudad = d.ciudad === 'Villa Gesell' ? 'Villa Gesell' : 'Mar del Plata';
		const nacimiento =
			typeof d.nacimiento === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(d.nacimiento)
				? d.nacimiento
				: undefined;
		try {
			await conSesion(event, 'PATCH', '/v1/yo', {
				cuerpo: {
					nombre: texto(d.nombre, 40),
					zona: texto(d.zona, 60),
					ciudad,
					bio: texto(d.bio, 300),
					trabajo: texto(d.trabajo, 60),
					fotos,
					prompts,
					intereses,
					...(nacimiento ? { nacimiento } : {})
				}
			});
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
		// Desde «Completá tu perfil» se sigue a donde iba.
		const volver = event.url.searchParams.get('volver');
		redirect(
			303,
			event.url.searchParams.has('completar')
				? volver?.startsWith('/app')
					? volver
					: '/app'
				: '/app/perfil'
		);
	}
};
