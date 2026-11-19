import { describe, expect, it } from 'vitest';
import {
	avisaA,
	caritasTexto,
	cuentaRegresiva,
	llegarDesde,
	puedeLlegar,
	diaCorto,
	enElDia,
	esTelefono,
	esVispera,
	lugarGuardado,
	numeroWhatsapp,
	puedeActivarTodoBien,
	semanaVisible,
	whatsapp
} from './dia.ts';

const ahora = new Date('2026-11-18T20:00:00-03:00'); // miércoles

describe('el día del plan', () => {
	it('«Hoy» va desde 3 h antes hasta 2 h después del final (la vuelta)', () => {
		const p = { empiezaEn: '2026-11-18T22:30:00-03:00', duracionMin: 120 };
		expect(enElDia(p, ahora)).toBe(true);
		expect(enElDia({ ...p, empiezaEn: '2026-11-19T00:00:00-03:00' }, ahora)).toBe(false);
		// Terminó a las 19 y son las 20: sigue (la vuelta); terminó a las 17, no.
		expect(enElDia({ ...p, empiezaEn: '2026-11-18T17:00:00-03:00' }, ahora)).toBe(true);
		expect(enElDia({ ...p, empiezaEn: '2026-11-18T15:00:00-03:00' }, ahora)).toBe(false);
		expect(puedeActivarTodoBien({ ...p, empiezaEn: '2026-11-18T17:00:00-03:00' }, ahora)).toBe(
			false
		);
		expect(enElDia({ ...p, cancelado: true }, ahora)).toBe(false);
	});
	it('WhatsApp con el 15 de la característica', () => {
		for (const t of [
			'0223 15 592-6772',
			'223 15 5926772',
			'2235926772',
			'0223 592-6772',
			'+54 9 223 592-6772'
		]) {
			expect(numeroWhatsapp(t), t).toBe('5492235926772');
		}
		expect(numeroWhatsapp('011 15 4444-1290')).toBe('5491144441290');
		expect(numeroWhatsapp('+598 99 123 456')).toBe('59899123456');
	});
	it('a quién avisa «¿Todo bien?»', () => {
		expect(avisaA({ contacto: 'mama@x.com' })).toBe('contacto');
		expect(avisaA({ contacto: '223 555 0101' })).toBe('vos');
		expect(avisaA({ contacto: 'x', avisa: 'contacto' })).toBe('contacto');
	});
	it('caritas y lugar guardado', () => {
		expect(caritasTexto({ promedio: 4.5, cantidad: 10 }).replace(/\u00a0/g, ' ')).toBe(
			'4,5 · 10+ opiniones'
		);
		expect(caritasTexto(undefined)).toBe('');
		expect(lugarGuardado('2026-11-18T20:40:00-03:00', ahora)).toBe('Es tuyo hasta las 20:40');
		expect(lugarGuardado('2026-11-18T19:40:00-03:00', ahora)).toBe('');
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

describe('puedeLlegar', () => {
	it('desde media hora antes hasta una hora después del final, como la API', () => {
		const p = { empiezaEn: '2026-11-20T23:30:00Z', duracionMin: 90 };
		expect(puedeLlegar(p, new Date('2026-11-20T22:59:00Z'))).toBe(false);
		expect(puedeLlegar(p, new Date('2026-11-20T23:00:00Z'))).toBe(true);
		expect(puedeLlegar(p, new Date('2026-11-21T02:00:00Z'))).toBe(true);
		expect(puedeLlegar(p, new Date('2026-11-21T02:01:00Z'))).toBe(false);
		expect(llegarDesde(p)).toBe('Check-in desde 20:00');
	});
});
