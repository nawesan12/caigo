import { describe, expect, it } from 'vitest';
import {
	cuentaRegresiva,
	diaCorto,
	enElDia,
	esTelefono,
	esVispera,
	semanaVisible,
	whatsapp
} from './dia.ts';

const ahora = new Date('2026-11-18T20:00:00-03:00'); // miércoles

describe('el día del plan', () => {
	it('«Hoy» va desde 3 h antes hasta el final', () => {
		const p = { empiezaEn: '2026-11-18T22:30:00-03:00', duracionMin: 120 };
		expect(enElDia(p, ahora)).toBe(true);
		expect(enElDia({ ...p, empiezaEn: '2026-11-19T00:00:00-03:00' }, ahora)).toBe(false);
		expect(enElDia({ ...p, empiezaEn: '2026-11-18T17:00:00-03:00' }, ahora)).toBe(false);
		expect(enElDia({ ...p, cancelado: true }, ahora)).toBe(false);
	});
	it('cuenta regresiva', () => {
		expect(cuentaRegresiva('2026-11-18T20:25:00-03:00', ahora)).toBe('en 25 min');
		expect(cuentaRegresiva('2026-11-18T22:10:00-03:00', ahora)).toBe('en 2 h 10');
		expect(cuentaRegresiva('2026-11-18T22:00:00-03:00', ahora)).toBe('en 2 h');
		expect(cuentaRegresiva('2026-11-18T19:00:00-03:00', ahora)).toBe('Ya arrancó');
	});
	it('víspera y semana', () => {
		expect(esVispera({ empiezaEn: '2026-11-19T21:30:00-03:00' }, ahora)).toBe(true);
		expect(esVispera({ empiezaEn: '2026-11-18T23:30:00-03:00' }, ahora)).toBe(false);
		expect(semanaVisible(ahora)).toBe(true);
		expect(semanaVisible(new Date('2026-11-20T12:00:00-03:00'))).toBe(false);
		expect(diaCorto('2026-11-23')).toEqual({ nombre: 'Lun', numero: 23 });
	});
	it('contactos', () => {
		expect(esTelefono('+54 9 223 555-0101')).toBe(true);
		expect(esTelefono('mama@caigo.com.ar')).toBe(false);
		expect(whatsapp('223 555 0101', 'hola')).toBe('https://wa.me/5492235550101?text=hola');
		expect(whatsapp('+54 9 223 555-0101', 'hola')).toBe('https://wa.me/5492235550101?text=hola');
	});
});
