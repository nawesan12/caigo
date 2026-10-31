import { describe, expect, it } from 'vitest';
import {
	agrupar,
	badgeMio,
	chipAhora,
	distancia,
	horario,
	leerUbicacion,
	reputacion,
	telefonoAR
} from './app.ts';
import type { Plan } from './tipos.ts';

// Miércoles 28 de octubre de 2026, 15:00 en Argentina.
const AHORA = new Date('2026-10-28T18:00:00Z');

describe('caigo desde la web', () => {
	it('lee la ubicación de la cookie', () => {
		expect(leerUbicacion('-38.0055,-57.5426')).toEqual([-38.0055, -57.5426]);
		expect(leerUbicacion('hola')).toBeNull();
		expect(leerUbicacion('-99,0')).toBeNull();
		expect(leerUbicacion(undefined)).toBeNull();
	});
	it('distancias como en el handoff', () => {
		expect(distancia(820)).toBe('800 m');
		expect(distancia(1210)).toBe('1,2 km');
		expect(distancia(24300)).toBe('24 km');
		expect(distancia(undefined)).toBe('');
	});
	it('horario y chip de Ahora', () => {
		expect(horario('2026-10-28T23:30:00Z', 60, AHORA)).toBe('Hoy, 20:30 a 21:30');
		expect(horario('2026-10-31T13:00:00Z', 120, AHORA)).toBe('Sábado 31, 10:00 a 12:00');
		expect(chipAhora('2026-10-28T20:00:00Z', AHORA)).toBe('Ahora · en 2 h');
		expect(chipAhora('2026-10-28T18:00:00Z', AHORA)).toBe('Ahora');
		expect(chipAhora('2026-10-28T18:40:00Z', AHORA)).toBe('Ahora · en 40 min');
		expect(chipAhora('2026-10-29T18:00:00Z', AHORA)).toBeNull();
	});
	it('reputación', () => {
		expect(reputacion({ fue: 23, de: 24 })).toBe('Fue a 23 de 24 planes · 96% asistencia');
		expect(reputacion({ fue: 0, de: 0 })).toBe('Nuevo en Caigo · todavía sin planes');
	});
	it('agrupa Mis planes', () => {
		const p = (empiezaEn: string) => ({ empiezaEn }) as Plan;
		const g = agrupar(
			[p('2026-10-28T23:00:00Z'), p('2026-10-30T23:00:00Z'), p('2026-11-20T23:00:00Z')],
			false,
			AHORA
		);
		expect(g.map((x) => x.titulo)).toEqual(['HOY', 'ESTA SEMANA', 'MÁS ADELANTE']);
		expect(agrupar([], true)).toEqual([]);
	});
	it('badge de Mis planes', () => {
		expect(badgeMio({ faltan: 0, somos: 4 })).toEqual({ texto: 'Ya somos 4', completo: true });
		expect(badgeMio({ faltan: 2, somos: 8 }).texto).toBe('Faltan 2');
	});
	it('teléfono', () => {
		expect(telefonoAR('223 555-0142')).toBe('2235550142');
		expect(telefonoAR('+54 9 223 555 0142')).toBe('+5492235550142');
	});
});
