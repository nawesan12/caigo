import { describe, expect, it } from 'vitest';
import { giro, INICIO, instruccion, paso, type Estado, type Lectura, type Punto } from './vida.ts';

/** Una malla mínima: nariz (1) y bordes (234, 454). */
function malla(nariz: number): Punto[] {
	const p: Punto[] = Array.from({ length: 468 }, () => ({ x: 0.5, y: 0.5 }));
	p[234] = { x: 0.35, y: 0.5 };
	p[454] = { x: 0.65, y: 0.5 };
	p[1] = { x: nariz, y: 0.5 };
	return p;
}

const frente: Lectura = { caras: 1, giro: 0, cx: 0.5, cy: 0.5, ancho: 0.35 };
const girada: Lectura = { caras: 1, giro: 0.7, cx: 0.58, cy: 0.5, ancho: 0.3 };
const nadie: Lectura = { caras: 0 };

function correr(lecturas: Lectura[], cada = 100): Estado {
	let e = INICIO;
	lecturas.forEach((l, i) => (e = paso(e, l, i * cada)));
	return e;
}

describe('prueba de vida', () => {
	it('mide el giro con la nariz entre los pómulos', () => {
		expect(giro(malla(0.5))).toBeCloseTo(0);
		expect(giro(malla(0.6))).toBeGreaterThan(0.5);
		expect(giro(malla(0.4))).toBeLessThan(-0.5);
	});
	it('sin cara no avanza', () => {
		expect(correr(Array(50).fill(nadie)).fase).toBe('buscando');
		expect(instruccion(INICIO, nadie)).toMatch(/No vemos tu cara/);
	});
	it('una cara quieta (una foto) no pasa de «girá»', () => {
		expect(correr(Array(80).fill(frente)).fase).toBe('girar');
	});
	it('de frente, gira y vuelve al centro: listo', () => {
		const e = correr([
			...Array(6).fill(frente),
			...Array(4).fill(girada),
			...Array(6).fill(frente)
		]);
		expect(e.fase).toBe('listo');
	});
	it('si se pierde la cara a mitad de camino, vuelve a empezar', () => {
		const e = correr([...Array(6).fill(frente), ...Array(4).fill(girada), nadie]);
		expect(e.fase).toBe('buscando');
	});
	it('pide centrarse, acercarse y una sola cara', () => {
		expect(instruccion(INICIO, { ...frente, cx: 0.85 })).toBe('Poné la cara en el centro');
		expect(instruccion(INICIO, { ...frente, ancho: 0.1 })).toBe('Acercate un poco más');
		expect(instruccion(INICIO, { ...frente, caras: 2 })).toBe('Que se vea una sola cara');
	});
});
