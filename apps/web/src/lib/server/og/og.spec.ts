import { writeFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { planDePrueba } from '#lib/prueba/plan.ts';
import type { Plan } from '#lib/tipos.ts';
import { FIJAS } from './fijas.ts';
import { png } from './motor.ts';
import { cuandoOg, ogPlan } from './plan.ts';
import { cortar, limpio, sinViudas } from './piezas.ts';
import { FOTOS } from './recursos.ts';

/** Con OG_SALIDA=/carpeta los PNG quedan ahí para mirarlos. */
const salida = process.env.OG_SALIDA;

function medidas(b: Uint8Array) {
	const v = new DataView(b.buffer, b.byteOffset);
	return { ancho: v.getUint32(16), alto: v.getUint32(20) };
}

async function dibujarPlan(nombre: string, p: Plan, foto: string | null) {
	const b = await png(
		ogPlan({
			plan: p,
			foto,
			anfitrion: p.lugar
				? { nombre: p.lugar.nombre, esLugar: true }
				: { nombre: p.organizador.nombre, esLugar: false },
			caras: p.van.map((v) => ({ nombre: v.nombre }))
		})
	);
	if (salida) writeFileSync(`${salida}/${nombre}.png`, b);
	return b;
}

describe('imágenes OG', () => {
	it('un plan: PNG de 1200×630 y liviano para WhatsApp', { timeout: 30_000 }, async () => {
		const b = await dibujarPlan('caso-gratis', planDePrueba(), FOTOS.playa);
		expect([...b.slice(0, 4)]).toEqual([0x89, 0x50, 0x4e, 0x47]);
		expect(medidas(b)).toEqual({ ancho: 1200, alto: 630 });
		expect(b.byteLength).toBeLessThan(400_000);
	});

	it(
		'casos raros: lugar con entrada, completo, cancelado, sin foto, título largo',
		{ timeout: 60_000 },
		async () => {
			const casos: [string, Plan, string | null][] = [
				[
					'caso-lugar-entrada',
					planDePrueba({
						titulo: 'Taller de cerámica con merienda',
						categoria: 'taller',
						cobro: 'entrada',
						precioCentavos: 1_200_000,
						lugar: { id: 'l1', slug: 'cafe-molienda', nombre: 'Café Molienda', zona: 'Güemes' },
						somos: 14,
						total: 20,
						faltan: 6
					}),
					FOTOS.lugar
				],
				['caso-completo', planDePrueba({ somos: 8, total: 8, faltan: 0 }), FOTOS.previa],
				['caso-cancelado', planDePrueba({ cancelado: true }), FOTOS.grupo],
				[
					'caso-largo-sin-foto',
					planDePrueba({
						titulo:
							'🔥 Partido de vóley mixto en la playa y después unas birras mirando el atardecer en Mogotes',
						categoria: 'deporte',
						foto: '',
						somos: 3,
						total: 30,
						faltan: 27
					}),
					null
				]
			];
			for (const [n, p, f] of casos) {
				const b = await dibujarPlan(n, p, f);
				expect(medidas(b)).toEqual({ ancho: 1200, alto: 630 });
			}
		}
	);

	it('las fijas', { timeout: 60_000 }, async () => {
		for (const [n, f] of Object.entries(FIJAS)) {
			const b = await png(f());
			if (salida) writeFileSync(`${salida}/fija-${n}.png`, b);
			expect(medidas(b)).toEqual({ ancho: 1200, alto: 630 });
		}
	});
});

describe('textos de las OG', () => {
	it('fecha absoluta en hora de Argentina', () => {
		expect(cuandoOg('2026-11-01T21:30:00Z')).toBe('Dom 1 nov · 18:30');
	});
	it('sin emojis ni renglones viudos', () => {
		expect(limpio('🔥 Fútbol 5 ⚽️ hoy')).toBe('Fútbol 5 hoy');
		expect(sinViudas('Truco en un bar de Güemes')).toBe('Truco en un bar de Güemes');
		expect(cortar('uno dos tres cuatro cinco', 12)).toBe('uno dos…');
	});
});
