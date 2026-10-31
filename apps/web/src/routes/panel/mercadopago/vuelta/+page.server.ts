import { redirect } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { elegirLugar } from '#lib/server/panel.ts';
import type { PageServerLoad } from './$types';

/**
 * Vuelta del OAuth de Mercado Pago (lugares.caigo.com.ar/mercadopago/vuelta?code&state). Se pasa a
 * la API, que verifica el state (PKCE) y guarda la conexión del lugar.
 */
export const load: PageServerLoad = async (event) => {
	const codigo = event.url.searchParams.get('code') ?? '';
	const estado = event.url.searchParams.get('state') ?? '';
	if (!codigo || !estado) redirect(303, '/cobros?mp=error');
	let destino: string;
	try {
		const r = await conSesion<{ lugarId: string }>(event, 'POST', '/v1/mercadopago/vuelta', {
			cuerpo: { codigo, estado }
		});
		if (r?.lugarId) elegirLugar(event, r.lugarId);
		destino = '/cobros?mp=ok';
	} catch (e) {
		if (!(e instanceof ErrorApi)) throw e;
		destino = `/cobros?mp=error&detalle=${encodeURIComponent(mensajeDe(e))}`;
	}
	redirect(303, destino);
};
