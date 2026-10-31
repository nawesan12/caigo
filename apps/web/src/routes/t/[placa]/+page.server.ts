import { error, fail } from '@sveltejs/kit';
import { aIngresar, api, conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import type { ResultadoTap, Yo } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

/**
 * Caigo Tap sin la app: la placa (chip NFC o QR) abre caigo.com.ar/t/<placa>. Si el celu tiene la
 * app, el sistema la abre antes de llegar acá; si no, se ofrece abrirla o hacer el check-in desde
 * la web. Una placa NTAG 424 agrega ?picc_data=…&cmac=… en cada lectura: se conservan tal cual.
 */
function lectura(placa: string, url: URL) {
	if (!/^[A-Za-z0-9._~-]{4,300}$/.test(placa)) error(404, 'Esta placa no existe.');
	const picc = url.searchParams.get('picc_data') ?? '';
	const cmac = url.searchParams.get('cmac') ?? '';
	if (picc.length > 200 || cmac.length > 100) error(400, 'La lectura de la placa no es válida.');
	return { placa, picc, cmac };
}

export const load: PageServerLoad = ({ params, url, locals, setHeaders }) => {
	const l = lectura(params.placa, url);
	setHeaders({ 'cache-control': 'no-store', 'x-robots-tag': 'noindex' });
	const q = new URLSearchParams();
	if (l.picc) q.set('picc_data', l.picc);
	if (l.cmac) q.set('cmac', l.cmac);
	return {
		app: `ar.com.caigo.app://t/${encodeURIComponent(l.placa)}${q.size ? '?' + q : ''}`,
		conSesion: !!locals.token
	};
};

export const actions: Actions = {
	default: async (event) => {
		const l = lectura(event.params.placa, event.url);
		if (!event.locals.token) aIngresar(event, event.url.pathname + event.url.search);
		try {
			const r = await conSesion<ResultadoTap>(event, 'POST', '/v1/tap', { cuerpo: l });
			let nombre = '';
			try {
				nombre = (await api<Yo>('GET', '/v1/yo', { token: event.locals.token })).nombre;
			} catch {
				nombre = '';
			}
			return { resultado: r, nombre };
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(e.estado >= 500 ? 503 : 400, { error: mensajeDe(e) });
		}
	}
};
