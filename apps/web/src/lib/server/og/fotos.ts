import { FOTOS } from './recursos.ts';

/**
 * Las fotos de un plan o de una persona, para meter en la imagen. satori sólo entiende PNG y JPEG,
 * así que se piden en esos formatos y lo demás (WebP, AVIF, SVG raros) se descarta: la imagen sale
 * igual, con el color de la categoría o la inicial.
 */

const TOPE = 3_000_000;
const ESPERA_MS = 2500;

/** /fotos/playa.jpg (de caigo.com.ar o relativa) → la copia embebida, sin salir a la red. */
function embebida(url: string): string | null {
	const m = /^(?:https?:\/\/(?:www\.)?caigo\.com\.ar)?\/fotos\/([a-z]+)\.jpg$/i.exec(url);
	return m ? (FOTOS[m[1].toLowerCase()] ?? null) : null;
}

/** Sólo de donde salen las fotos de Caigo (nada de pedirle al servidor URLs arbitrarias). */
function permitido(host: string, base: string): boolean {
	const h = host.toLowerCase();
	return (
		h === 'caigo.com.ar' ||
		h.endsWith('.caigo.com.ar') ||
		h === 'img.clerk.com' ||
		h === 'images.clerk.dev' ||
		h === new URL(base).hostname
	);
}

function tipoDe(bytes: Uint8Array): 'image/png' | 'image/jpeg' | null {
	if (bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47)
		return 'image/png';
	if (bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return 'image/jpeg';
	return null;
}

/** Baja una imagen y la devuelve como data: URL, o null si no se pudo (nunca tira). */
export async function fotoComoDato(
	url: string | null | undefined,
	o: { base: string; api?: string; f?: typeof fetch }
): Promise<string | null> {
	const f = o.f ?? fetch;
	if (!url) return null;
	const propia = embebida(url);
	if (propia) return propia;
	let abs: URL;
	try {
		abs = new URL(url, o.base);
	} catch {
		return null;
	}
	if (abs.protocol !== 'https:' && abs.protocol !== 'http:') return null;
	if (!permitido(abs.hostname, o.base)) return null;
	// Las fotos subidas viven en la API: desde el servidor se piden por adentro.
	if (o.api && abs.hostname === 'api.caigo.com.ar') abs = new URL(abs.pathname + abs.search, o.api);
	// Clerk devuelve WebP si lo aceptás: pedimos JPEG/PNG y un tamaño razonable.
	if (abs.hostname === 'img.clerk.com') abs.searchParams.set('width', '160');
	try {
		const r = await f(abs, {
			headers: { accept: 'image/jpeg,image/png;q=0.9' },
			signal: AbortSignal.timeout(ESPERA_MS)
		});
		if (!r.ok) return null;
		const largo = Number(r.headers.get('content-length') ?? 0);
		if (largo > TOPE) return null;
		const bytes = new Uint8Array(await r.arrayBuffer());
		if (bytes.byteLength > TOPE) return null;
		const tipo = tipoDe(bytes);
		if (!tipo) return null;
		return `data:${tipo};base64,${Buffer.from(bytes).toString('base64')}`;
	} catch {
		return null;
	}
}
