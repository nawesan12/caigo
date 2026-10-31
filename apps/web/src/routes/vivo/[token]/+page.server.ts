import { error } from '@sveltejs/kit';
import { api, ErrorApi, ipDe } from '#lib/server/api.ts';
import type { EnVivo } from '#lib/tipos.ts';
import type { PageServerLoad } from './$types';

/** "Compartir mi plan en vivo": lo que ve el contacto de confianza, sin sesión. */
export const load: PageServerLoad = async (event) => {
	const { params, setHeaders } = event;
	if (!/^[A-Za-z0-9._~-]{8,400}$/.test(params.token)) error(404, 'Este link ya no está activo.');
	setHeaders({ 'cache-control': 'no-store', 'x-robots-tag': 'noindex' });
	try {
		const vivo = await api<EnVivo>('GET', `/v1/publico/vivo/${encodeURIComponent(params.token)}`, {
			ip: ipDe(event)
		});
		return { vivo };
	} catch (e) {
		if (e instanceof ErrorApi && (e.estado === 404 || e.estado === 400)) error(404, e.detalle);
		throw e;
	}
};
