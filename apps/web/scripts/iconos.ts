/**
 * Genera los íconos de la web (favicon, apple-touch-icon y los del manifest) a partir del ícono de
 * la app de iOS (1024×1024). Sólo JS/WASM: resvg-wasm para achicar y upng-js para escribir el PNG.
 * Se corre a mano cuando cambia el ícono y los archivos quedan en static/:
 *
 *   bun scripts/iconos.ts
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { initWasm, Resvg } from '@resvg/resvg-wasm';
import UPNG from 'upng-js';

const aca = dirname(fileURLToPath(import.meta.url));
const web = join(aca, '..');
const fuente = join(web, '../ios/Caigo/Recursos/Assets.xcassets/AppIcon.appiconset/AppIcon.png');
const FONDO = '#F4EADF'; // el crema del borde del ícono, para el maskable

await initWasm(readFileSync(join(web, 'node_modules/@resvg/resvg-wasm/index_bg.wasm')));
const b64 = readFileSync(fuente).toString('base64');

/** El ícono a `tam` px; `escala` < 1 lo achica sobre el fondo (zona segura del maskable). */
function icono(tam: number, o: { escala?: number; radio?: number } = {}): Uint8Array {
	const e = o.escala ?? 1;
	const lado = 1024 * e;
	const off = (1024 - lado) / 2;
	const r = o.radio ?? 0;
	const svg = `<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="1024" height="1024" viewBox="0 0 1024 1024">
	<defs><clipPath id="c"><rect width="1024" height="1024" rx="${r}" ry="${r}"/></clipPath></defs>
	<g clip-path="url(#c)"><rect width="1024" height="1024" fill="${FONDO}"/>
	<image x="${off}" y="${off}" width="${lado}" height="${lado}" xlink:href="data:image/png;base64,${b64}"/></g></svg>`;
	const r2 = new Resvg(svg, { fitTo: { mode: 'width', value: tam } });
	const out = r2.render();
	const png = new Uint8Array(UPNG.encode([out.pixels.slice().buffer], out.width, out.height, 0));
	out.free();
	r2.free();
	return png;
}

function guardar(nombre: string, datos: Uint8Array | string) {
	writeFileSync(join(web, 'static', nombre), datos);
	console.log(nombre, typeof datos === 'string' ? datos.length : datos.byteLength, 'bytes');
}

// Manifest y Organization (logo): cuadrados completos; el sistema les pone su máscara.
guardar('icono-512.png', icono(512));
guardar('icono-192.png', icono(192));
// Maskable: el dibujo al 80 % para que ningún recorte (círculo, gota) lo corte.
guardar('icono-maskable-512.png', icono(512, { escala: 0.8 }));
// iOS redondea solo: va cuadrado y sin transparencia.
guardar('apple-touch-icon.png', icono(180));
// Pestaña del navegador: con las esquinas redondeadas del ícono.
const f32 = icono(32, { radio: 220 });
const f16 = icono(16, { radio: 220 });
guardar('favicon-32.png', f32);
const f64 = icono(64, { radio: 220 });
guardar(
	'favicon.svg',
	`<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="64" height="64" viewBox="0 0 64 64"><image width="64" height="64" xlink:href="data:image/png;base64,${Buffer.from(f64).toString('base64')}"/></svg>\n`
);

// favicon.ico con dos PNG adentro (16 y 32): el formato ICO acepta PNG tal cual.
function ico(pngs: [number, Uint8Array][]): Uint8Array {
	const cab = Buffer.alloc(6 + 16 * pngs.length);
	cab.writeUInt16LE(0, 0);
	cab.writeUInt16LE(1, 2);
	cab.writeUInt16LE(pngs.length, 4);
	let off = cab.length;
	pngs.forEach(([tam, png], i) => {
		const e = 6 + i * 16;
		cab.writeUInt8(tam, e);
		cab.writeUInt8(tam, e + 1);
		cab.writeUInt8(0, e + 2);
		cab.writeUInt8(0, e + 3);
		cab.writeUInt16LE(1, e + 4);
		cab.writeUInt16LE(32, e + 6);
		cab.writeUInt32LE(png.byteLength, e + 8);
		cab.writeUInt32LE(off, e + 12);
		off += png.byteLength;
	});
	return new Uint8Array(Buffer.concat([cab, ...pngs.map(([, p]) => Buffer.from(p))]));
}
guardar(
	'favicon.ico',
	ico([
		[16, f16],
		[32, f32]
	])
);
