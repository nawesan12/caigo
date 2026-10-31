/**
 * Generador de QR chico, sin dependencias (para la placa imprimible de Caigo Tap). Modo byte,
 * corrección M, versiones 1 a 10 (hasta 213 bytes: alcanza para la URL firmada de una placa).
 * Sigue el algoritmo de la norma ISO/IEC 18004 tal como lo explica el generador de Nayuki.
 */

// Por versión (índice = versión): codewords de corrección por bloque y cantidad de bloques (nivel M).
const ECC_POR_BLOQUE = [0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26];
const BLOQUES = [0, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5];
const ALINEACION = [
	[],
	[],
	[6, 18],
	[6, 22],
	[6, 26],
	[6, 30],
	[6, 34],
	[6, 22, 38],
	[6, 24, 42],
	[6, 26, 46],
	[6, 28, 50]
];

/** Módulos de datos (bits) de una versión. */
function modulosCrudos(v: number): number {
	let r = (16 * v + 128) * v + 64;
	if (v >= 2) {
		const n = Math.floor(v / 7) + 2;
		r -= (25 * n - 10) * n - 55;
		if (v >= 7) r -= 36;
	}
	return r;
}
const codewordsTotales = (v: number) => Math.floor(modulosCrudos(v) / 8);
const codewordsDatos = (v: number) => codewordsTotales(v) - ECC_POR_BLOQUE[v] * BLOQUES[v];

function mul(x: number, y: number): number {
	let z = 0;
	for (let i = 7; i >= 0; i--) {
		z = (z << 1) ^ ((z >>> 7) * 0x11d);
		z ^= ((y >>> i) & 1) * x;
	}
	return z;
}

function divisor(grado: number): number[] {
	const r = new Array<number>(grado).fill(0);
	r[grado - 1] = 1;
	let raiz = 1;
	for (let i = 0; i < grado; i++) {
		for (let j = 0; j < grado; j++) {
			r[j] = mul(r[j], raiz);
			if (j + 1 < grado) r[j] ^= r[j + 1];
		}
		raiz = mul(raiz, 0x02);
	}
	return r;
}

function resto(datos: number[], div: number[]): number[] {
	const r = new Array<number>(div.length).fill(0);
	for (const b of datos) {
		const f = b ^ (r.shift() as number);
		r.push(0);
		div.forEach((c, i) => (r[i] ^= mul(c, f)));
	}
	return r;
}

/** La matriz del QR: `true` es un módulo oscuro. Tira si el texto no entra en la versión 10. */
export function qr(texto: string): boolean[][] {
	const bytes = [...new TextEncoder().encode(texto)];
	let v = 1;
	for (; v <= 10; v++) {
		const bitsCuenta = v < 10 ? 8 : 16;
		if (4 + bitsCuenta + bytes.length * 8 <= codewordsDatos(v) * 8) break;
	}
	if (v > 10) throw new Error('El texto es muy largo para el QR.');

	// Bits: modo byte, largo, datos, terminador y relleno.
	const bits: number[] = [];
	const poner = (val: number, n: number) => {
		for (let i = n - 1; i >= 0; i--) bits.push((val >>> i) & 1);
	};
	poner(0x4, 4);
	poner(bytes.length, v < 10 ? 8 : 16);
	for (const b of bytes) poner(b, 8);
	const cap = codewordsDatos(v) * 8;
	poner(0, Math.min(4, cap - bits.length));
	poner(0, (8 - (bits.length % 8)) % 8);
	for (let pad = 0xec; bits.length < cap; pad ^= 0xec ^ 0x11) poner(pad, 8);
	const datos: number[] = [];
	for (let i = 0; i < bits.length; i += 8) {
		let b = 0;
		for (let j = 0; j < 8; j++) b = (b << 1) | bits[i + j];
		datos.push(b);
	}

	// Bloques con corrección, intercalados.
	const nb = BLOQUES[v];
	const ecc = ECC_POR_BLOQUE[v];
	const total = codewordsTotales(v);
	const cortos = nb - (total % nb);
	const largoCorto = Math.floor(total / nb);
	const div = divisor(ecc);
	const bloques: number[][] = [];
	for (let i = 0, k = 0; i < nb; i++) {
		const d = datos.slice(k, k + largoCorto - ecc + (i < cortos ? 0 : 1));
		k += d.length;
		const e = resto(d, div);
		if (i < cortos) d.push(0);
		bloques.push(d.concat(e));
	}
	const cw: number[] = [];
	for (let i = 0; i < bloques[0].length; i++)
		bloques.forEach((b, j) => {
			if (i !== largoCorto - ecc || j >= cortos) cw.push(b[i]);
		});

	// Patrones fijos.
	const n = v * 4 + 17;
	const m: boolean[][] = Array.from({ length: n }, () => new Array<boolean>(n).fill(false));
	const fijo: boolean[][] = Array.from({ length: n }, () => new Array<boolean>(n).fill(false));
	const fijar = (x: number, y: number, o: boolean) => {
		m[y][x] = o;
		fijo[y][x] = true;
	};
	for (let i = 0; i < n; i++) {
		fijar(6, i, i % 2 === 0);
		fijar(i, 6, i % 2 === 0);
	}
	for (const [cx, cy] of [
		[3, 3],
		[n - 4, 3],
		[3, n - 4]
	])
		for (let dy = -4; dy <= 4; dy++)
			for (let dx = -4; dx <= 4; dx++) {
				const x = cx + dx,
					y = cy + dy;
				if (x < 0 || y < 0 || x >= n || y >= n) continue;
				const d = Math.max(Math.abs(dx), Math.abs(dy));
				fijar(x, y, d !== 2 && d !== 4);
			}
	const al = ALINEACION[v];
	for (let i = 0; i < al.length; i++)
		for (let j = 0; j < al.length; j++) {
			if (
				(i === 0 && j === 0) ||
				(i === 0 && j === al.length - 1) ||
				(i === al.length - 1 && j === 0)
			)
				continue;
			for (let dy = -2; dy <= 2; dy++)
				for (let dx = -2; dx <= 2; dx++)
					fijar(al[i] + dx, al[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1);
		}
	const formato = (mascara: number) => {
		const d = (0 << 3) | mascara; // nivel M = 0
		let r = d;
		for (let i = 0; i < 10; i++) r = (r << 1) ^ ((r >>> 9) * 0x537);
		const b = ((d << 10) | r) ^ 0x5412;
		const bit = (i: number) => ((b >>> i) & 1) !== 0;
		for (let i = 0; i <= 5; i++) fijar(8, i, bit(i));
		fijar(8, 7, bit(6));
		fijar(8, 8, bit(7));
		fijar(7, 8, bit(8));
		for (let i = 9; i < 15; i++) fijar(14 - i, 8, bit(i));
		for (let i = 0; i < 8; i++) fijar(n - 1 - i, 8, bit(i));
		for (let i = 8; i < 15; i++) fijar(8, n - 15 + i, bit(i));
		fijar(8, n - 8, true);
	};
	formato(0);
	if (v >= 7) {
		let r = v;
		for (let i = 0; i < 12; i++) r = (r << 1) ^ ((r >>> 11) * 0x1f25);
		const b = (v << 12) | r;
		for (let i = 0; i < 18; i++) {
			const o = ((b >>> i) & 1) !== 0;
			const a = n - 11 + (i % 3),
				c = Math.floor(i / 3);
			fijar(a, c, o);
			fijar(c, a, o);
		}
	}

	// Datos en zigzag.
	let i = 0;
	for (let der = n - 1; der >= 1; der -= 2) {
		if (der === 6) der = 5;
		for (let vert = 0; vert < n; vert++)
			for (let j = 0; j < 2; j++) {
				const x = der - j;
				const y = ((der + 1) & 2) === 0 ? n - 1 - vert : vert;
				if (!fijo[y][x] && i < cw.length * 8) {
					m[y][x] = ((cw[i >>> 3] >>> (7 - (i & 7))) & 1) !== 0;
					i++;
				}
			}
	}

	// La máscara con menos penalidad.
	const condicion = (k: number, x: number, y: number) =>
		[
			(x + y) % 2 === 0,
			y % 2 === 0,
			x % 3 === 0,
			(x + y) % 3 === 0,
			(Math.floor(x / 3) + Math.floor(y / 2)) % 2 === 0,
			((x * y) % 2) + ((x * y) % 3) === 0,
			(((x * y) % 2) + ((x * y) % 3)) % 2 === 0,
			(((x + y) % 2) + ((x * y) % 3)) % 2 === 0
		][k];
	const enmascarar = (k: number) => {
		for (let y = 0; y < n; y++)
			for (let x = 0; x < n; x++) if (!fijo[y][x] && condicion(k, x, y)) m[y][x] = !m[y][x];
	};
	let mejor = 0,
		menor = Infinity;
	for (let k = 0; k < 8; k++) {
		enmascarar(k);
		formato(k);
		const p = penalidad(m);
		if (p < menor) {
			menor = p;
			mejor = k;
		}
		enmascarar(k);
	}
	enmascarar(mejor);
	formato(mejor);
	return m;
}

function penalidad(m: boolean[][]): number {
	const n = m.length;
	let p = 0;
	const lineas: string[] = [];
	for (let y = 0; y < n; y++) {
		lineas.push(m[y].map((o) => (o ? '1' : '0')).join(''));
		lineas.push(m.map((fila) => (fila[y] ? '1' : '0')).join(''));
	}
	for (const l of lineas) {
		for (const r of l.match(/0{5,}|1{5,}/g) ?? []) p += r.length - 2;
		const pad = '0000' + l + '0000';
		p +=
			40 *
			((pad.match(/(?=10111010000)/g) ?? []).length + (pad.match(/(?=00001011101)/g) ?? []).length);
	}
	let oscuros = 0;
	for (let y = 0; y < n; y++)
		for (let x = 0; x < n; x++) {
			if (m[y][x]) oscuros++;
			if (x < n - 1 && y < n - 1) {
				const c = m[y][x];
				if (c === m[y][x + 1] && c === m[y + 1][x] && c === m[y + 1][x + 1]) p += 3;
			}
		}
	const total = n * n;
	p += (Math.ceil(Math.abs(oscuros * 20 - total * 10) / total) - 1) * 10;
	return p;
}

/** El QR como `d` de un <path> SVG (con 4 módulos de margen) y su lado en módulos. */
export function qrSvg(texto: string): { d: string; lado: number } {
	const m = qr(texto);
	let d = '';
	m.forEach((fila, y) =>
		fila.forEach((o, x) => {
			if (o) d += `M${x + 4} ${y + 4}h1v1h-1z`;
		})
	);
	return { d, lado: m.length + 8 };
}
