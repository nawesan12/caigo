import { partes } from './fechas.ts';
import { CATEGORIAS, faltanTexto, primerNombre } from './plan.ts';
import { cargo, pesos, sena } from './precios.ts';
import type { Plan } from './tipos.ts';

/**
 * SEO: descripciones, datos estructurados (schema.org en JSON-LD) y el sitemap. Todo puro, sin
 * SvelteKit, para poder probarlo.
 */

export type Ld = Record<string, unknown>;

/** JSON-LD para meter en <script type="application/ld+json"> sin que un "</script>" lo corte. */
export function ldTexto(ld: Ld | Ld[]): string {
	return JSON.stringify(ld)
		.replace(/</g, '\\u003c')
		.replace(/>/g, '\\u003e')
		.replace(/&/g, '\\u0026')
		.replace(/\u2028/g, '\\u2028')
		.replace(/\u2029/g, '\\u2029');
}

/** "2026-11-01T21:30:00Z" → "2026-11-01T18:30:00-03:00" (Argentina, UTC−3 fijo). */
export function isoArgentina(f: string | Date): string {
	const p = partes(f);
	const d = new Date(new Date(f).getTime() - 3 * 3600_000);
	const dd = (n: number) => String(n).padStart(2, '0');
	return `${p.anio}-${dd(p.mes + 1)}-${dd(p.dia)}T${p.hora}:${dd(d.getUTCSeconds())}-03:00`;
}

/** Recorta a `max` caracteres en un límite de palabra, con "…". */
export function recortar(t: string, max = 160): string {
	const s = t.replace(/\s+/g, ' ').trim();
	if (s.length <= max) return s;
	const c = s.slice(0, max - 1);
	const i = c.lastIndexOf(' ');
	return (i > max * 0.6 ? c.slice(0, i) : c).replace(/[\s,.;:·-]+$/, '') + '…';
}

const MESES = [
	'enero',
	'febrero',
	'marzo',
	'abril',
	'mayo',
	'junio',
	'julio',
	'agosto',
	'septiembre',
	'octubre',
	'noviembre',
	'diciembre'
];
const DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];

/** "el sábado 1 de noviembre a las 18:30" (absoluta: los buscadores la guardan días). */
export function cuandoTexto(f: string): string {
	const p = partes(f);
	return `el ${DIAS[p.semana]} ${p.dia} de ${MESES[p.mes]} a las ${p.hora}`;
}

/** Quién invita: el lugar, o el primer nombre de quien organiza (nunca el apellido). */
export function anfitrionDe(p: Plan): string {
	return p.lugar?.nombre || primerNombre(p.organizador?.nombre ?? '') || 'Alguien';
}

/** "Zona Playa Grande, Mar del Plata" (o el lugar, si es de un lugar aliado). */
export function dondeDe(p: Plan): string {
	const sitio = p.lugar?.nombre || (p.zona ? `Zona ${p.zona}` : p.lugarNombre);
	return [sitio, p.ciudad].filter(Boolean).join(', ');
}

/**
 * La descripción de /p/<slug> (meta description y og:description): quién invita, qué, cuándo,
 * dónde y cuánta gente falta. Hasta 160 caracteres.
 */
export function descripcionPlan(p: Plan): string {
	const quien = anfitrionDe(p);
	const d = partes(p.empiezaEn);
	const corto = `el ${DIAS[d.semana].slice(0, 3)} ${d.dia}/${d.mes + 1} a las ${d.hora}`;
	const frase = (cuando: string) => `${quien} te invita a ${p.titulo} ${cuando}, ${dondeDe(p)}.`;
	const cierres = p.cancelado
		? ['El plan se canceló: mirá otros en Caigo.', 'Se canceló.']
		: p.faltan <= 0
			? ['Ya está completo: mirá otros planes en Caigo.', 'Ya está completo.']
			: [
					`${faltanTexto(p.faltan)}: sumate desde la web, sin bajar la app.`,
					`${faltanTexto(p.faltan)}: sumate en Caigo.`,
					`${faltanTexto(p.faltan)}.`
				];
	// La más completa que entre en 160; si ninguna entra (título muy largo), se recorta.
	for (const cuando of [cuandoTexto(p.empiezaEn), corto])
		for (const cierre of cierres) {
			const t = `${frase(cuando)} ${cierre}`;
			if (t.length <= 160) return t;
		}
	return recortar(`${frase(corto)} ${cierres.at(-1)}`);
}

/** Organization + WebSite (portada). */
export function ldSitio(sitio: string): Ld[] {
	return [
		{
			'@context': 'https://schema.org',
			'@type': 'Organization',
			'@id': `${sitio}/#organizacion`,
			name: 'Caigo',
			url: `${sitio}/`,
			logo: { '@type': 'ImageObject', url: `${sitio}/icono-512.png`, width: 512, height: 512 },
			slogan: 'Hacé planes. Conocé gente.',
			email: 'hola@caigo.com.ar',
			areaServed: [
				{ '@type': 'City', name: 'Mar del Plata' },
				{ '@type': 'City', name: 'Villa Gesell' }
			],
			parentOrganization: {
				'@type': 'Organization',
				name: 'Grupo Vimo',
				url: 'https://grupovimo.com.ar'
			}
		},
		{
			'@context': 'https://schema.org',
			'@type': 'WebSite',
			'@id': `${sitio}/#sitio`,
			name: 'Caigo',
			url: `${sitio}/`,
			inLanguage: 'es-AR',
			publisher: { '@id': `${sitio}/#organizacion` },
			potentialAction: {
				'@type': 'SearchAction',
				target: {
					'@type': 'EntryPoint',
					urlTemplate: `${sitio}/app/explorar?q={search_term_string}`
				},
				'query-input': 'required name=search_term_string'
			}
		}
	];
}

/** Migas de pan: [["Caigo", "/"], ["Explorar", "/app/explorar"], ["Fútbol 5…", "/p/…"]]. */
export function ldMigas(sitio: string, items: [string, string][]): Ld {
	return {
		'@context': 'https://schema.org',
		'@type': 'BreadcrumbList',
		itemListElement: items.map(([nombre, ruta], i) => ({
			'@type': 'ListItem',
			position: i + 1,
			name: nombre,
			item: sitio + ruta
		}))
	};
}

/** El plan como Event de schema.org (para que Google lo pueda mostrar en sus resultados). */
export function ldPlan(p: Plan, o: { sitio: string; imagen: string }): Ld {
	const url = `${o.sitio}/p/${p.slug}`;
	const fin = new Date(new Date(p.empiezaEn).getTime() + Math.max(p.duracionMin || 0, 30) * 60_000);
	const publica = !p.ocultarDireccion;
	const direccion: Ld = {
		'@type': 'PostalAddress',
		addressLocality: p.ciudad,
		addressRegion: 'Provincia de Buenos Aires',
		addressCountry: 'AR'
	};
	if (publica && p.direccion?.texto) direccion.streetAddress = p.direccion.texto;
	const lugar: Ld = {
		'@type': 'Place',
		// Con la dirección oculta, sólo la zona y la ciudad (la exacta llega al aceptar).
		name:
			p.lugar?.nombre ||
			(publica && p.lugarNombre) ||
			[p.zona, p.ciudad].filter(Boolean).join(', '),
		address: direccion
	};
	const lat = p.direccion?.lat ?? p.lat;
	const lng = p.direccion?.lng ?? p.lng;
	if (publica && typeof lat === 'number' && typeof lng === 'number')
		lugar.geo = { '@type': 'GeoCoordinates', latitude: lat, longitude: lng };

	const ld: Ld = {
		'@context': 'https://schema.org',
		'@type': 'Event',
		name: p.titulo,
		description: recortar(p.descripcion || descripcionPlan(p), 300),
		url,
		image: [o.imagen],
		inLanguage: 'es-AR',
		startDate: isoArgentina(p.empiezaEn),
		endDate: isoArgentina(fin),
		eventAttendanceMode: 'https://schema.org/OfflineEventAttendanceMode',
		eventStatus: p.cancelado
			? 'https://schema.org/EventCancelled'
			: 'https://schema.org/EventScheduled',
		location: lugar,
		organizer: p.lugar
			? { '@type': 'Organization', name: p.lugar.nombre }
			: { '@type': 'Person', name: anfitrionDe(p) },
		maximumAttendeeCapacity: p.total,
		remainingAttendeeCapacity: Math.max(0, p.faltan),
		isAccessibleForFree: p.cobro === 'gratis',
		keywords: [CATEGORIAS[p.categoria] ?? p.categoria, p.zona, p.ciudad].filter(Boolean).join(', ')
	};
	if (p.cobro !== 'gratis' && p.precioCentavos) {
		const precio = p.precioCentavos / 100;
		// Lo que paga cada persona: el precio más el cargo por servicio (como en el checkout).
		const total = precio + cargo(precio);
		ld.offers = {
			'@type': 'Offer',
			url,
			price: total.toFixed(2),
			priceCurrency: 'ARS',
			availability:
				p.cancelado || p.faltan <= 0 ? 'https://schema.org/SoldOut' : 'https://schema.org/InStock',
			...(p.cobro === 'sena'
				? {
						description: `Reservás con una seña de ${pesos(sena(precio, p.senaPct ?? 0) + cargo(precio))}; el resto se paga en el lugar.`
					}
				: {})
		};
	}
	return ld;
}

/** Una URL del sitemap. */
export type UrlSitemap = { loc: string; lastmod?: string; changefreq?: string; priority?: number };

function xml(s: string): string {
	return s
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
		.replace(/"/g, '&quot;')
		.replace(/'/g, '&apos;');
}

export function sitemapXml(urls: UrlSitemap[]): string {
	const filas = urls.map((u) => {
		const partes = [`<loc>${xml(u.loc)}</loc>`];
		if (u.lastmod) partes.push(`<lastmod>${u.lastmod}</lastmod>`);
		if (u.changefreq) partes.push(`<changefreq>${u.changefreq}</changefreq>`);
		if (u.priority !== undefined) partes.push(`<priority>${u.priority.toFixed(1)}</priority>`);
		return `<url>${partes.join('')}</url>`;
	});
	return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${filas.join('\n')}\n</urlset>\n`;
}

/** Las páginas fijas de caigo.com.ar (lastmod: cuándo cambió el contenido de cada una). */
export const PAGINAS_FIJAS: UrlSitemap[] = [
	{ loc: '/', lastmod: '2026-10-31', changefreq: 'daily', priority: 1 },
	{ loc: '/app/explorar', lastmod: '2026-10-31', changefreq: 'hourly', priority: 0.9 },
	{ loc: '/plus', lastmod: '2026-10-31', changefreq: 'monthly', priority: 0.6 },
	{ loc: '/terminos', lastmod: '2026-10-31', changefreq: 'yearly', priority: 0.3 },
	{ loc: '/privacidad', lastmod: '2026-10-31', changefreq: 'yearly', priority: 0.3 },
	{ loc: '/arrepentimiento', lastmod: '2026-10-31', changefreq: 'yearly', priority: 0.2 }
];

/**
 * El sitemap de caigo.com.ar: las fijas y cada plan público que todavía no pasó. Los planes van
 * sin lastmod: la API no dice cuándo se editó uno, y un lastmod inventado hace que Google deje de
 * creerle a todo el sitemap.
 */
export function sitemapCaigo(sitio: string, planes: Plan[], ahora = new Date()): string {
	const vistos = new Set<string>();
	const delPlan: UrlSitemap[] = [];
	for (const p of planes) {
		if (!p.slug || vistos.has(p.slug) || p.cancelado) continue;
		if (p.visibilidad && p.visibilidad !== 'publico') continue;
		if (new Date(p.empiezaEn).getTime() < ahora.getTime()) continue;
		vistos.add(p.slug);
		delPlan.push({
			loc: `${sitio}/p/${p.slug}`,
			changefreq: 'hourly',
			priority: 0.8
		});
	}
	return sitemapXml([...PAGINAS_FIJAS.map((u) => ({ ...u, loc: sitio + u.loc })), ...delPlan]);
}

export function robotsCaigo(sitio: string): string {
	return [
		'User-agent: *',
		'Allow: /',
		'Allow: /app/explorar',
		'Disallow: /app$',
		'Disallow: /app/',
		'Disallow: /equipo',
		'Disallow: /ingresar',
		'Disallow: /salir',
		'Disallow: /sso-callback',
		'Disallow: /archivo/',
		'Disallow: /vivo/',
		'Disallow: /t/',
		'Disallow: /panel',
		'Disallow: /*?pago=',
		'',
		`Sitemap: ${sitio}/sitemap.xml`,
		''
	].join('\n');
}

/** lugares.caigo.com.ar: sólo la entrada al panel (y las legales); lo demás pide sesión. */
export function robotsLugares(panel: string): string {
	return [
		'User-agent: *',
		'Allow: /$',
		'Allow: /ingresar',
		'Allow: /terminos',
		'Allow: /privacidad',
		'Disallow: /',
		'',
		`Sitemap: ${panel}/sitemap.xml`,
		''
	].join('\n');
}

export function sitemapLugares(panel: string): string {
	return sitemapXml([
		{ loc: `${panel}/ingresar`, lastmod: '2026-10-31', changefreq: 'monthly', priority: 1 }
	]);
}
