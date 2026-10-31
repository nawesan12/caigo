import { describe, expect, it } from 'vitest';
import { cargo, desglose, leerPrecio, pesos, pesosDeCentavos, sena } from './precios.ts';

describe('cargo por servicio', () => {
	it('es el 7,5 % redondeado a $100', () => {
		expect(cargo(8000)).toBe(600);
		expect(cargo(4500)).toBe(300); // 337,5 → 300
		expect(cargo(5000)).toBe(400); // 375 → 400
		expect(cargo(12000)).toBe(900);
		expect(cargo(0)).toBe(0);
	});
});

describe('seña', () => {
	it('es el % del precio redondeado a $100', () => {
		expect(sena(8000, 30)).toBe(2400);
		expect(sena(8500, 30)).toBe(2600); // 2550 → 2600 (como el panel del handoff)
		expect(sena(12000, 20)).toBe(2400);
	});
});

describe('desglose', () => {
	it('entrada: la persona paga precio + cargo, el lugar recibe el precio', () => {
		expect(desglose('entrada', 8000, 30)).toMatchObject({ paga: 8600, recibe: 8000, cargo: 600 });
	});
	it('seña: la persona paga la seña + el cargo del precio entero; el resto, en el lugar', () => {
		expect(desglose('sena', 8000, 30)).toMatchObject({
			paga: 3000,
			recibe: 2400,
			resto: 5600,
			cargo: 600
		});
	});
});

describe('formato', () => {
	it('pesos argentinos sin decimales', () => {
		expect(pesos(25600)).toBe('$25.600');
		expect(pesos(999)).toBe('$999');
		expect(pesosDeCentavos(599900)).toBe('$5.999');
		expect(pesosDeCentavos(null)).toBe('$0');
	});
	it('lee lo que se escribe en el precio', () => {
		expect(leerPrecio('8.000')).toBe(8000);
		expect(leerPrecio('$ 12000')).toBe(12000);
		expect(leerPrecio('')).toBe(0);
	});
});
