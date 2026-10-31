import { error, fail, redirect } from '@sveltejs/kit';
import { aIngresar, api, conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import type { Plan } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

async function publico(slug: string): Promise<Plan> {
	if (!/^[a-z0-9-]{1,80}$/.test(slug)) error(404, 'Ese plan no existe o es privado.');
	try {
		return await api<Plan>('GET', `/v1/publico/planes/${encodeURIComponent(slug)}`);
	} catch (e) {
		if (e instanceof ErrorApi && e.estado === 404) error(404, e.detalle);
		throw e;
	}
}

export const load: PageServerLoad = async ({ params, locals, url, setHeaders }) => {
	const plan = await publico(params.slug);
	// Con sesión, la vista completa dice si ya estás adentro o esperando.
	let miEstado = '';
	if (locals.token) {
		try {
			const mio = await api<Plan>('GET', `/v1/planes/${plan.id}`, { token: locals.token });
			miEstado = mio.miEstado ?? '';
		} catch {
			miEstado = '';
		}
	} else {
		// Sin sesión es igual para todos: WhatsApp y compañía pueden cachearlo un rato.
		setHeaders({ 'cache-control': 'public, max-age=60' });
	}
	const pago = url.searchParams.get('pago');
	return {
		plan,
		miEstado,
		conSesion: !!locals.token,
		pago: pago === 'ok' || pago === 'pendiente' || pago === 'error' ? pago : null
	};
};

export const actions: Actions = {
	sumarme: async (event) => {
		const { params, locals } = event;
		if (!locals.token) aIngresar(event, `/p/${params.slug}`);
		const plan = await publico(params.slug);
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
			return fail(e.estado === 400 ? 400 : 409, { error: mensajeDe(e), codigo: e.codigo });
		}
	}
};
