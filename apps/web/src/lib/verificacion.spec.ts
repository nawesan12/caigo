import { describe, expect, it } from 'vitest';
import { edad, fechaValida, pasoDe, verificada } from './verificacion.ts';

describe('verificación de /app', () => {
	it('pide teléfono, selfie y DNI aprobado', () => {
		expect(verificada({ telefono: '+54223', selfie: true, dni: 'aprobada' })).toBe(true);
		expect(verificada({ telefono: '+54223', selfie: true, dni: 'pendiente' })).toBe(false);
		expect(verificada({ telefono: '+54223', selfie: true, dni: 'rechazada' })).toBe(false);
		expect(verificada({ telefono: '+54223', selfie: false, dni: 'aprobada' })).toBe(false);
		expect(verificada({ selfie: true, dni: 'aprobada' })).toBe(false);
		expect(verificada(null)).toBe(false);
	});
	it('el paso que falta, en orden', () => {
		expect(pasoDe({ selfie: false, dni: 'ninguna' })).toBe(1);
		expect(pasoDe({ telefono: '1', selfie: false, dni: 'ninguna' })).toBe(2);
		expect(pasoDe({ telefono: '1', selfie: true, dni: 'ninguna' })).toBe(3);
		expect(pasoDe({ telefono: '1', selfie: true, dni: 'rechazada' })).toBe(3);
		expect(pasoDe({ telefono: '1', selfie: true, dni: 'aprobada' })).toBe(0);
	});
});

describe('fechaValida', () => {
	it('acepta fechas que existen', () => {
		expect(fechaValida('2000-02-29')).toEqual([2000, 2, 29]);
	});
	it('rechaza las que no existen o no tienen la forma', () => {
		expect(fechaValida('2005-02-31')).toBeNull();
		expect(fechaValida('2001-02-29')).toBeNull();
		expect(fechaValida('2005-13-01')).toBeNull();
		expect(fechaValida('5-1-2005')).toBeNull();
	});
});

describe('edad', () => {
	it('cuenta con la fecha de Argentina, no la UTC', () => {
		// 18/11 a las 23:30 en Argentina ya es 19/11 en UTC: quien cumple el 19 todavía no cumplió.
		const ahora = new Date('2026-11-19T02:30:00Z');
		expect(edad([2008, 11, 19], ahora)).toBe(17);
		expect(edad([2008, 11, 18], ahora)).toBe(18);
	});
});
