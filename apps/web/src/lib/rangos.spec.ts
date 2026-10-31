import { describe, expect, it } from 'vitest';
import { rangoFecha } from './app.ts';

describe('rangoFecha', () => {
	// Jueves 30/10/2026 15:00 en Argentina.
	const jue = new Date('2026-10-30T18:00:00Z');
	it('hoy y mañana', () => {
		expect(rangoFecha('hoy', jue)).toEqual({
			desde: '2026-10-30T00:00:00-03:00',
			hasta: '2026-10-31T00:00:00-03:00',
			dias: ['2026-10-30']
		});
		expect(rangoFecha('manana', jue)?.dias).toEqual(['2026-10-31']);
	});
	it('el finde es sábado y domingo', () => {
		expect(rangoFecha('finde', jue)?.dias).toEqual(['2026-10-31', '2026-11-01']);
		expect(rangoFecha('finde', jue)?.hasta).toBe('2026-11-02T00:00:00-03:00');
	});
	it('un domingo, el finde es lo que queda', () => {
		expect(rangoFecha('finde', new Date('2026-11-01T15:00:00Z'))?.dias).toEqual(['2026-11-01']);
	});
	it('un día elegido, dentro de los próximos 60', () => {
		expect(rangoFecha('2026-11-20', jue)?.dias).toEqual(['2026-11-20']);
		expect(rangoFecha('2027-05-01', jue)).toBeNull();
		expect(rangoFecha('cualquiera', jue)).toBeNull();
	});
});
