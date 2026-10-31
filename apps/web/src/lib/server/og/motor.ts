import satori from 'satori';
import { initWasm, Resvg } from '@resvg/resvg-wasm';
import UPNG from 'upng-js';
// Todo viaja dentro del bundle del servidor (build/server): el contenedor node:22-alpine sólo
// recibe build/, sin node_modules ni binarios nativos. `?inline` lo deja como data: URL en base64.
import wasm from '@resvg/resvg-wasm/index_bg.wasm?inline';
import geist500 from './recursos/Geist-500.ttf?inline';
import geist600 from './recursos/Geist-600.ttf?inline';
import geist700 from './recursos/Geist-700.ttf?inline';
import geist800 from './recursos/Geist-800.ttf?inline';

/**
 * Motor de las imágenes de Open Graph: un árbol de nodos (estilo JSX, sin React) → SVG con satori
 * → PNG con resvg compilado a WASM. 100 % JS/WASM: corre igual en la Mac y en Alpine.
 */

export const ANCHO = 1200;
export const ALTO = 630;

export type Nodo = {
	type: string;
	props: Record<string, unknown> & { style?: Estilo; children?: Hijo | Hijo[] };
};
export type Hijo = Nodo | string | number | null | undefined | false;
export type Estilo = Record<string, string | number>;

/** Un div (o lo que sea) con flex por defecto: satori lo pide si hay más de un hijo. */
export function h(type: string, style: Estilo = {}, ...children: Hijo[]): Nodo {
	const hijos = children.flat().filter((c) => c !== null && c !== undefined && c !== false);
	// satori usa content-box por defecto; acá todo se mide como en el CSS de la web (border-box).
	// Y todo posicionado: así se pinta en el orden del árbol (si no, los fondos absolutos tapan
	// el contenido, como en CSS).
	const s =
		type === 'div'
			? { display: 'flex', boxSizing: 'border-box', position: 'relative', ...style }
			: style;
	return { type, props: { style: s, children: hijos.length === 1 ? hijos[0] : hijos } };
}

/** Una imagen (src en data: URL o http). */
export function img(src: string, ancho: number, alto: number, style: Estilo = {}): Nodo {
	return { type: 'img', props: { src, width: ancho, height: alto, style } };
}

/** data: URL (de `?inline`) → bytes. */
export function bytesDe(dataUrl: string): Uint8Array {
	// Copia a un ArrayBuffer propio (Buffer.from puede compartir el pool interno de Node).
	return new Uint8Array(Buffer.from(dataUrl.slice(dataUrl.indexOf(',') + 1), 'base64'));
}

let listo: Promise<void> | null = null;
function iniciar(): Promise<void> {
	listo ??= initWasm(bytesDe(wasm)).catch((e) => {
		// Si ya se inició (por ejemplo, en desarrollo con recarga en caliente), seguimos.
		if (String(e).includes('Already initialized')) return;
		listo = null;
		throw e;
	});
	return listo;
}

const FUENTES = [
	{ name: 'Geist', data: bytesDe(geist500), weight: 500 as const, style: 'normal' as const },
	{ name: 'Geist', data: bytesDe(geist600), weight: 600 as const, style: 'normal' as const },
	{ name: 'Geist', data: bytesDe(geist700), weight: 700 as const, style: 'normal' as const },
	{ name: 'Geist', data: bytesDe(geist800), weight: 800 as const, style: 'normal' as const }
];

// De a una por vez: el contenedor tiene 192 MB y cada dibujo usa unos 30 MB de WASM y píxeles.
let cola: Promise<unknown> = Promise.resolve();

/** Dibuja el árbol en un PNG de 1200×630. */
export function png(raiz: Nodo, ancho = ANCHO, alto = ALTO): Promise<Uint8Array> {
	const turno = cola.then(() => dibujar(raiz, ancho, alto));
	cola = turno.catch(() => undefined);
	return turno;
}

async function dibujar(raiz: Nodo, ancho: number, alto: number): Promise<Uint8Array> {
	const svg = await satori(raiz as unknown as Parameters<typeof satori>[0], {
		width: ancho,
		height: alto,
		fonts: FUENTES.map((f) => ({ ...f, data: f.data.buffer as ArrayBuffer }))
	});
	await iniciar();
	const r = new Resvg(svg, { fitTo: { mode: 'width', value: ancho } });
	const out = r.render();
	// Paleta de 256 colores: de ~900 KB a ~250 KB. WhatsApp no muestra la vista
	// previa si la imagen pesa mucho, y a este tamaño la diferencia no se ve.
	const bytes = new Uint8Array(
		UPNG.encode([out.pixels.slice().buffer as ArrayBuffer], out.width, out.height, 256)
	);
	out.free();
	r.free();
	return bytes;
}
