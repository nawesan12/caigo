import { fail, redirect } from '@sveltejs/kit';
import { aIngresar, conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import type { EstadoPlus } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

type Plus = {
	estado: EstadoPlus;
	planes: { plan: string; producto: string; centavos: number; nombre: string }[];
};

type Recibidos = { personas: { id: string; foto?: string }[]; bloqueada: boolean };

export const load: PageServerLoad = async (event) => {
	const vuelta = event.url.searchParams.get('vuelta') === '1';
	if (!event.locals.token) return { plus: null, vuelta, conSesion: false, teSumaron: [] };
	try {
		const [plus, recibidos] = await Promise.all([
			conSesion<Plus>(event, 'GET', '/v1/plus'),
			// La tarjeta "N personas te sumaron" del paywall: si falla, no se muestra.
			conSesion<Recibidos>(event, 'GET', '/v1/gente/recibidos?modo=gente').catch(() => null)
		]);
		const teSumaron = (recibidos?.personas ?? []).map((p) => p.foto ?? '');
		return { plus, vuelta, conSesion: true, teSumaron };
	} catch (e) {
		if (!(e instanceof ErrorApi)) throw e;
		return { plus: null, vuelta, conSesion: true, teSumaron: [], error: mensajeDe(e) };
	}
};

export const actions: Actions = {
	default: async (event) => {
		const plan = String((await event.request.formData()).get('plan') ?? '');
		if (!['semana', 'mes', 'temporada'].includes(plan))
			return fail(400, { error: 'Elegí un plan.', plan: 'mes' });
		if (!event.locals.token) aIngresar(event, '/plus');
		let link: string;
		try {
			link = (
				await conSesion<{ link: string }>(event, 'POST', '/v1/plus/mercadopago', {
					cuerpo: { plan }
				})
			).link;
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e), plan });
		}
		redirect(303, link, { external: true });
	}
};
