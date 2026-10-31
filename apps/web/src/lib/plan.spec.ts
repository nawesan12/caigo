import { describe, expect, it } from 'vitest';
import { cobroTexto, faltanTexto, iniciales, lista, primerNombre } from './plan.ts';

describe('textos del plan', () => {
	it('el contador', () => {
		expect(faltanTexto(0)).toBe('Completo');
		expect(faltanTexto(1)).toBe('Falta 1');
		expect(faltanTexto(4)).toBe('Faltan 4');
	});
	it('nombres', () => {
		expect(lista(['Nico', 'Lu', 'Cami'])).toBe('Nico, Lu y Cami');
		expect(lista(['Nico'])).toBe('Nico');
		expect(primerNombre('Nicolás Pérez')).toBe('Nicolás');
		expect(iniciales('Café Molienda')).toBe('CM');
	});
});

describe('chip de cobro', () => {
	it('como en el panel del handoff', () => {
		expect(cobroTexto({ cobro: 'sena', precioCentavos: 800000, senaPct: 30 })).toBe(
			'Seña 30% · $8.000'
		);
		expect(cobroTexto({ cobro: 'entrada', precioCentavos: 450000 })).toBe('Entrada $4.500');
		expect(cobroTexto({ cobro: 'gratis' })).toBe('Gratis');
	});
});
