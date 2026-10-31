import { PUBLIC_PANEL, PUBLIC_SITIO } from '$app/env/public';
import { robotsCaigo, robotsLugares } from '#lib/seo.ts';
import type { RequestHandler } from './$types';

/** robots.txt de cada host: caigo.com.ar abre lo público; lugares.caigo.com.ar sólo su entrada. */
export const GET: RequestHandler = ({ locals }) => {
	const texto =
		locals.sitio === 'lugares'
			? robotsLugares(PUBLIC_PANEL.replace(/\/+$/, ''))
			: robotsCaigo(PUBLIC_SITIO.replace(/\/+$/, ''));
	return new Response(texto, {
		headers: {
			'content-type': 'text/plain; charset=utf-8',
			'cache-control': 'public, max-age=3600'
		}
	});
};
