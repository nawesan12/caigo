import { describe, expect, it } from 'vitest';
import { aRFC3339, cuandoCorto, cuandoLargo, diasParaElegir, esHoy, fechaDeHoy } from './fechas.ts';

// Martes 28 de octubre de 2026, 15:00 en Argentina.
const AHORA = new Date('2026-10-28T18:00:00Z');

describe('fechas en hora de Argentina', () => {
	it('chips del panel', () => {
		expect(cuandoCorto('2026-10-28T22:30:00Z', AHORA)).toBe('Hoy 19:30');
		expect(cuandoCorto('2026-10-30T00:00:00Z', AHORA)).toBe('Mañana 21:00');
		expect(cuandoCorto('2026-10-31T00:00:00Z', AHORA)).toBe('Vie 30 · 21:00');
		// 02:30 UTC del 29 es todavía el 28 a las 23:30 en Argentina.
		expect(cuandoCorto('2026-10-29T02:30:00Z', AHORA)).toBe('Hoy 23:30');
	});
	it('link público', () => {
		expect(cuandoLargo('2026-10-29T00:30:00Z', AHORA)).toBe('Hoy, 21:30');
		expect(cuandoLargo('2026-11-01T01:00:00Z', AHORA)).toBe('Sábado 31 de octubre, 22:00');
	});
	it('encabezado y hoy', () => {
		expect(fechaDeHoy(AHORA)).toBe('Miércoles 28 de octubre');
		expect(esHoy('2026-10-28T23:59:00-03:00', AHORA)).toBe(true);
		expect(esHoy('2026-10-29T00:00:00-03:00', AHORA)).toBe(false);
	});
	it('días para elegir y RFC 3339', () => {
		const d = diasParaElegir(AHORA);
		expect(d.map((x) => x.etiqueta)).toEqual(['Hoy', 'Mañana', 'Vie 30', 'Sáb 31', 'Dom 1']);
		expect(d[0].valor).toBe('2026-10-28');
		expect(aRFC3339('2026-10-31', '19:30')).toBe('2026-10-31T19:30:00-03:00');
	});
});
