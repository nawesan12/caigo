import { describe, expect, it } from 'vitest';
import { faltanTexto, iniciales, lista, primerNombre } from './plan.ts';

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
