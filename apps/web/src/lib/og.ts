import type { Plan } from './tipos.ts';

/**
 * Las imágenes de Open Graph llevan la versión en la URL (?v=…): cuando cambia el diseño o los
 * datos que se ven (cupo, título, foto…), cambia la URL y WhatsApp/Facebook/X la vuelven a pedir.
 * Con la versión correcta, la imagen se cachea un año.
 */

/** Subirlo cada vez que cambie el diseño de las imágenes. */
export const DISENO_OG = '3';

/** FNV-1a de 32 bits en base 36: corto, estable y sin depender de node:crypto. */
export function huella(s: string): string {
	let x = 0x811c9dc5;
	for (let i = 0; i < s.length; i++) {
		x ^= s.charCodeAt(i);
		x = Math.imul(x, 0x01000193) >>> 0;
	}
	return x.toString(36);
}

/** Lo que se ve en la imagen de un plan: si cambia algo de esto, cambia la versión. */
export function versionOgPlan(p: Plan): string {
	return huella(
		JSON.stringify([
			DISENO_OG,
			p.titulo,
			p.categoria,
			p.empiezaEn,
			p.somos,
			p.total,
			p.faltan,
			p.cancelado,
			p.foto,
			p.zona,
			p.ciudad,
			p.lugarNombre,
			p.organizador?.nombre,
			p.organizador?.foto,
			p.lugar?.nombre,
			p.lugar?.foto,
			p.cobro,
			p.precioCentavos ?? 0,
			p.senaPct ?? 0,
			(p.van ?? []).slice(0, 4).map((v) => [v.nombre, v.foto ?? ''])
		])
	);
}

export type OgFija = 'inicio' | 'plus' | 'lugares' | 'caigo';

export const OG_FIJAS: Record<OgFija, { alt: string }> = {
	inicio: {
		alt: 'Caigo: hacé planes, conocé gente. Tarjetas de planes en Mar del Plata y Villa Gesell a los que les falta gente.'
	},
	plus: { alt: 'Caigo Plus: más planes, más gente, sin límites.' },
	lugares: {
		alt: 'Caigo para lugares: publicá planes con entrada o seña y llená tu lugar en Mar del Plata y Villa Gesell.'
	},
	caigo: { alt: 'Caigo: hacé planes, conocé gente.' }
};

export const VERSION_FIJAS = huella(DISENO_OG + ':fijas');

export function urlOgFija(nombre: OgFija): string {
	return `/og/${nombre}.png?v=${VERSION_FIJAS}`;
}

export function urlOgPlan(p: Plan): string {
	return `/p/${p.slug}/og.png?v=${versionOgPlan(p)}`;
}
