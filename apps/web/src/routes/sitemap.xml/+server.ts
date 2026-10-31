import { PUBLIC_PANEL, PUBLIC_SITIO } from '$app/env/public';
import { api, ipDe } from '#lib/server/api.ts';
import { sitemapCaigo, sitemapLugares } from '#lib/seo.ts';
import type { Plan } from '#lib/tipos.ts';
import type { RequestHandler } from './$types';

const CIUDADES = ['Mar del Plata', 'Villa Gesell'];

/**
 * sitemap.xml: en caigo.com.ar las páginas fijas y los planes públicos de los próximos dos meses
 * (Explorar devuelve hasta 60 por pedido: se pide por ciudad y se juntan); en lugares, su entrada.
 */
export const GET: RequestHandler = async (event) => {
	const cabeceras = {
		'content-type': 'application/xml; charset=utf-8',
		'cache-control': 'public, max-age=3600'
	};
	if (event.locals.sitio === 'lugares')
		return new Response(sitemapLugares(PUBLIC_PANEL.replace(/\/+$/, '')), { headers: cabeceras });

	const ahora = new Date();
	const hasta = new Date(ahora.getTime() + 59 * 86400_000).toISOString();
	const ip = ipDe(event);
	const listas = await Promise.all(
		[...CIUDADES, ''].map((ciudad) => {
			const q = new URLSearchParams({ hasta });
			if (ciudad) q.set('ciudad', ciudad);
			return api<{ planes: Plan[] | null }>('GET', `/v1/publico/planes/explorar?${q}`, { ip })
				.then((r) => r.planes ?? [])
				.catch(() => [] as Plan[]);
		})
	);
	const xml = sitemapCaigo(PUBLIC_SITIO.replace(/\/+$/, ''), listas.flat(), ahora);
	return new Response(xml, { headers: cabeceras });
};
