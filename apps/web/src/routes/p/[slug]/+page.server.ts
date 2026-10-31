import { error, fail, redirect } from '@sveltejs/kit';
import { aIngresar, api, conSesion, ErrorApi, ipDe, mensajeDe } from '#lib/server/api.ts';
import type { Plan } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

async function publico(slug: string, ip: string | null): Promise<Plan> {
	if (!/^[a-z0-9-]{1,80}$/.test(slug)) error(404, 'Ese plan no existe o es privado.');
	try {
		return await api<Plan>('GET', `/v1/publico/planes/${encodeURIComponent(slug)}`, { ip });
	} catch (e) {
		if (e instanceof ErrorApi && e.estado === 404) error(404, e.detalle);
		throw e;
	}
}

export const load: PageServerLoad = async (event) => {
	const { params, locals, url, setHeaders } = event;
	const plan = await publico(params.slug, ipDe(event));
	const pago = url.searchParams.get('pago');
	const pagoOk = pago === 'ok' || pago === 'pendiente' || pago === 'error' ? pago : null;
	// Con sesión, el plan se ve en Caigo desde la web (con Sumarme, chat y todo lo demás).
	if (locals.token) redirect(303, `/app/plan/${plan.id}${pagoOk ? `?pago=${pagoOk}` : ''}`);
	// Sin sesión es igual para todos: WhatsApp y compañía pueden cachearlo un rato.
	setHeaders({ 'cache-control': 'public, max-age=60' });
	return { plan, miEstado: '', conSesion: false, pago: pagoOk };
};

export const actions: Actions = {
	sumarme: async (event) => {
		const { params, locals } = event;
		if (!locals.token) aIngresar(event, `/p/${params.slug}`);
		const plan = await publico(params.slug, ipDe(event));
		try {
			if (plan.lugar && plan.cobro !== 'gratis') {
				// Entrada o seña de un lugar: Checkout Pro de Mercado Pago.
				const c = await conSesion<{ link?: string }>(
					event,
					'POST',
					`/v1/planes/${plan.id}/entradas`,
					{
						cuerpo: { cantidad: 1 }
					}
				);
				if (c?.link) redirect(303, c.link);
				return { estado: 'aceptada' };
			}
			const r = await conSesion<{ estado: string }>(
				event,
				'POST',
				`/v1/planes/${plan.id}/sumarme`,
				{
					cuerpo: { cantidad: 1 }
				}
			);
			return { estado: r.estado };
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			// Sin selfie: a verificarse y de vuelta al plan (en Caigo desde la web).
			if (e.codigo === 'falta_selfie')
				redirect(303, `/app/verificar?volver=${encodeURIComponent(`/app/plan/${plan.id}`)}`);
			// Plan sólo para mujeres o diversidades y sin género cargado: lo pregunta el detalle de /app.
			if (e.codigo === 'falta_genero') redirect(303, `/app/plan/${plan.id}`);
			return fail(e.estado === 400 ? 400 : 409, { error: mensajeDe(e), codigo: e.codigo });
		}
	}
};
