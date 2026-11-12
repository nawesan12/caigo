import { describe, expect, it } from 'vitest';
import { planDePrueba } from './prueba/plan.ts';
import { huella, versionOgPlan } from './og.ts';
import {
	descripcionPlan,
	isoArgentina,
	ldMigas,
	ldPlan,
	ldSitio,
	ldTexto,
	robotsCaigo,
	robotsLugares,
	sitemapCaigo
} from './seo.ts';

const SITIO = 'https://caigo.com.ar';
const IMG = `${SITIO}/p/previa-al-atardecer-en-playa-grande-6e23/og.png?v=abc`;

describe('JSON-LD del plan (Event)', () => {
	it('fechas en hora de Argentina y fin según la duración', () => {
		const ld = ldPlan(planDePrueba(), { sitio: SITIO, imagen: IMG });
		expect(ld['@type']).toBe('Event');
		expect(ld.startDate).toBe('2026-11-01T18:30:00-03:00');
		// 150 minutos después.
		expect(ld.endDate).toBe('2026-11-01T21:00:00-03:00');
		expect(ld.eventAttendanceMode).toBe('https://schema.org/OfflineEventAttendanceMode');
		expect(ld.eventStatus).toBe('https://schema.org/EventScheduled');
		expect(ld.image).toEqual([IMG]);
		expect(ld.url).toBe(`${SITIO}/p/previa-al-atardecer-en-playa-grande-6e23`);
		expect(ld.remainingAttendeeCapacity).toBe(4);
		expect(ld.isAccessibleForFree).toBe(true);
		expect(ld.offers).toBeUndefined();
	});

	it('dirección oculta: sólo zona y ciudad, sin geo ni calle', () => {
		const ld = ldPlan(
			planDePrueba({
				ocultarDireccion: true,
				direccion: { texto: 'Av. Siempreviva 742', lat: -38.03, lng: -57.53 }
			}),
			{ sitio: SITIO, imagen: IMG }
		);
		const lugar = ld.location as Record<string, unknown>;
		expect(lugar.name).toBe('Playa Grande, Mar del Plata');
		expect(lugar.geo).toBeUndefined();
		expect(JSON.stringify(lugar)).not.toContain('Siempreviva');
		expect((lugar.address as Record<string, unknown>).addressLocality).toBe('Mar del Plata');
	});

	it('dirección pública: calle y coordenadas', () => {
		const ld = ldPlan(
			planDePrueba({
				ocultarDireccion: false,
				lugarNombre: 'Bar La Cumbre',
				direccion: { texto: 'Güemes 2800', lat: -38.01, lng: -57.55 }
			}),
			{ sitio: SITIO, imagen: IMG }
		);
		const lugar = ld.location as Record<string, Record<string, unknown>>;
		expect(lugar.name).toBe('Bar La Cumbre');
		expect(lugar.address.streetAddress).toBe('Güemes 2800');
		expect(lugar.geo).toEqual({ '@type': 'GeoCoordinates', latitude: -38.01, longitude: -57.55 });
	});

	it('entrada de un lugar: Offer en ARS con el cargo, agotada si está completo', () => {
		const base = {
			cobro: 'entrada' as const,
			precioCentavos: 1_200_000,
			lugar: { id: 'l1', slug: 'cafe-molienda', nombre: 'Café Molienda', zona: 'Güemes' }
		};
		const abierto = ldPlan(planDePrueba(base), { sitio: SITIO, imagen: IMG });
		expect(abierto.organizer).toEqual({ '@type': 'Organization', name: 'Café Molienda' });
		expect(abierto.offers).toMatchObject({
			'@type': 'Offer',
			price: '12900.00',
			priceCurrency: 'ARS',
			availability: 'https://schema.org/InStock'
		});
		const lleno = ldPlan(planDePrueba({ ...base, somos: 8, faltan: 0 }), {
			sitio: SITIO,
			imagen: IMG
		});
		expect((lleno.offers as Record<string, unknown>).availability).toBe(
			'https://schema.org/SoldOut'
		);
	});

	it('seña: aclara cuánto se paga al reservar', () => {
		const ld = ldPlan(planDePrueba({ cobro: 'sena', precioCentavos: 800_000, senaPct: 30 }), {
			sitio: SITIO,
			imagen: IMG
		});
		expect((ld.offers as Record<string, string>).description).toContain('$3.000');
	});

	it('cancelado', () => {
		const ld = ldPlan(planDePrueba({ cancelado: true }), { sitio: SITIO, imagen: IMG });
		expect(ld.eventStatus).toBe('https://schema.org/EventCancelled');
	});

	it('organizador: sólo el primer nombre', () => {
		const ld = ldPlan(planDePrueba(), { sitio: SITIO, imagen: IMG });
		expect(ld.organizer).toEqual({ '@type': 'Person', name: 'Nahuel' });
	});

	it('se puede meter en un <script> y vuelve a parsear igual', () => {
		const p = planDePrueba({ titulo: 'Truco </script><script>alert(1)</script> & birra' });
		const ld = ldPlan(p, { sitio: SITIO, imagen: IMG });
		const texto = ldTexto(ld);
		expect(texto).not.toContain('</script>');
		expect(texto).not.toContain('<');
		expect(JSON.parse(texto)).toEqual(ld);
	});
});

describe('otros JSON-LD', () => {
	it('Organization y WebSite en la portada', () => {
		const [org, web] = ldSitio(SITIO);
		expect(org['@type']).toBe('Organization');
		expect(web['@type']).toBe('WebSite');
		expect(JSON.stringify(web)).toContain('/app/explorar?q={search_term_string}');
	});
	it('migas', () => {
		const m = ldMigas(SITIO, [
			['Caigo', '/'],
			['Explorar', '/app/explorar']
		]);
		expect(m.itemListElement).toEqual([
			{ '@type': 'ListItem', position: 1, name: 'Caigo', item: `${SITIO}/` },
			{ '@type': 'ListItem', position: 2, name: 'Explorar', item: `${SITIO}/app/explorar` }
		]);
	});
});

describe('textos', () => {
	it('isoArgentina', () => {
		expect(isoArgentina('2026-11-06T00:30:00Z')).toBe('2026-11-05T21:30:00-03:00');
	});
	it('descripción del plan entre 100 y 160 caracteres', () => {
		const d = descripcionPlan(planDePrueba());
		expect(d).toBe(
			'Nahuel te invita a Previa al atardecer en Playa Grande el domingo 1 de noviembre a las 18:30, Zona Playa Grande, Mar del Plata. Faltan 4: sumate en Caigo.'
		);
		expect(d.length).toBeLessThanOrEqual(160);
		expect(descripcionPlan(planDePrueba({ faltan: 0 }))).toContain('completo');
	});
	it('la versión de la OG cambia con el cupo', () => {
		expect(versionOgPlan(planDePrueba())).not.toBe(versionOgPlan(planDePrueba({ faltan: 3 })));
		expect(huella('caigo')).toBe(huella('caigo'));
	});
});

describe('sitemap y robots', () => {
	const ahora = new Date('2026-10-31T12:00:00Z');

	it('fijas + planes públicos futuros, sin repetidos ni cancelados ni pasados', () => {
		const xml = sitemapCaigo(
			SITIO,
			[
				planDePrueba(),
				planDePrueba(), // repetido (vino en dos ciudades)
				planDePrueba({ slug: 'truco-vn9f', empiezaEn: '2026-11-06T00:30:00Z' }),
				planDePrueba({ slug: 'cancelado-1', cancelado: true }),
				planDePrueba({ slug: 'paso-1', empiezaEn: '2026-10-30T20:00:00Z' }),
				planDePrueba({ slug: 'privado-1', visibilidad: 'gente' })
			],
			ahora
		);
		expect(xml.startsWith('<?xml version="1.0" encoding="UTF-8"?>')).toBe(true);
		const locs = [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1]);
		expect(locs).toEqual([
			`${SITIO}/`,
			`${SITIO}/app/explorar`,
			`${SITIO}/plus`,
			`${SITIO}/terminos`,
			`${SITIO}/privacidad`,
			`${SITIO}/arrepentimiento`,
			`${SITIO}/borrar-cuenta`,
			`${SITIO}/p/previa-al-atardecer-en-playa-grande-6e23`,
			`${SITIO}/p/truco-vn9f`
		]);
		expect(xml).toContain('<lastmod>2026-10-31</lastmod>');
		// Cada <url> bien cerrado.
		expect(xml.match(/<url>/g)?.length).toBe(xml.match(/<\/url>/g)?.length);
	});

	it('escapa lo que haga falta en XML', () => {
		const xml = sitemapCaigo('https://caigo.com.ar/?a=1&b=2', [], ahora);
		expect(xml).toContain('&amp;b=2');
	});

	it('robots: lo privado afuera, Explorar adentro, con sitemap', () => {
		const r = robotsCaigo(SITIO);
		for (const d of ['/app/', '/equipo', '/ingresar', '/salir', '/sso-callback', '/vivo/', '/t/'])
			expect(r).toContain(`Disallow: ${d}`);
		expect(r).toContain('Allow: /app/explorar');
		expect(r).toContain(`Sitemap: ${SITIO}/sitemap.xml`);
		const l = robotsLugares('https://lugares.caigo.com.ar');
		expect(l).toContain('Disallow: /');
		expect(l).toContain('Allow: /ingresar');
		expect(l).toContain('Sitemap: https://lugares.caigo.com.ar/sitemap.xml');
	});
});
