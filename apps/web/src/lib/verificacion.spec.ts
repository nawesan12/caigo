import { describe, expect, it } from 'vitest';
import { pasoDe, verificada } from './verificacion.ts';

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
