/**
 * De dónde llegó alguien (contrato §8): `utm:<fuente>`, `ref:<quién>` o `p:<slug>` (abrió el link
 * de un plan). Lo guarda y lo manda lib/server/origen.ts.
 */

/** "Instagram Stories" → "instagram-stories": minúsculas, sin espacios raros, corto. */
function limpiar(v: string): string {
	return v
		.normalize('NFD')
		.replace(/[̀-ͯ]/g, '')
		.toLowerCase()
		.replace(/[^a-z0-9._-]+/g, '-')
		.replace(/^-+|-+$/g, '')
		.slice(0, 40);
}

/** El origen de esta visita, o '' si no trae ninguno. */
export function origenDe(url: URL): string {
	const utm = limpiar(url.searchParams.get('utm_source') ?? '');
	if (utm) return `utm:${utm}`;
	const ref = limpiar(url.searchParams.get('ref') ?? '');
	if (ref) return `ref:${ref}`;
	const p = /^\/p\/([a-z0-9-]{1,80})\/?$/.exec(url.pathname);
	if (p) return `p:${p[1].slice(0, 58)}`;
	return '';
}

/** Lo que se acepta mandar a la API. */
export const ORIGEN_VALIDO = /^(utm|ref|p):[a-z0-9._-]{1,60}$/;
