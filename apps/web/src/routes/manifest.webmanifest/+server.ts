import type { RequestHandler } from './$types';

const ICONOS = [
	{ src: '/icono-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
	{ src: '/icono-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
	{ src: '/icono-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' }
];

/** El manifest de cada host: Caigo (claro, arranca en /app) o Caigo para lugares (grafito). */
export const GET: RequestHandler = ({ locals }) => {
	const lugares = locals.sitio === 'lugares';
	const manifest = lugares
		? {
				id: '/',
				name: 'Caigo para lugares',
				short_name: 'Caigo lugares',
				description:
					'Publicá planes con entrada o seña, cobrá con Mercado Pago y hacé el check-in con Caigo Tap.',
				lang: 'es-AR',
				start_url: '/',
				scope: '/',
				display: 'standalone',
				background_color: '#F3F0E9',
				theme_color: '#201D2B',
				icons: ICONOS
			}
		: {
				id: '/app',
				name: 'Caigo',
				short_name: 'Caigo',
				description:
					'Hacé planes. Conocé gente. Planes a los que les falta gente en Mar del Plata y Villa Gesell.',
				lang: 'es-AR',
				start_url: '/app',
				scope: '/',
				display: 'standalone',
				orientation: 'portrait',
				background_color: '#FAF9F6',
				theme_color: '#FAF9F6',
				categories: ['social', 'lifestyle', 'entertainment'],
				icons: ICONOS
			};
	return new Response(JSON.stringify(manifest), {
		headers: {
			'content-type': 'application/manifest+json; charset=utf-8',
			'cache-control': 'public, max-age=86400'
		}
	});
};
