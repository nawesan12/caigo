import { describe, expect, it } from 'vitest';
import { esOscuro } from './tema-mapa.ts';

describe('esOscuro', () => {
	it('sigue la cookie y, en «sistema», al teléfono', () => {
		expect(esOscuro('oscuro', false)).toBe(true);
		expect(esOscuro('claro', true)).toBe(false);
		expect(esOscuro('sistema', true)).toBe(true);
		expect(esOscuro('sistema', false)).toBe(false);
		// Fuera de /app (panel, equipo) no hay data-tema: claro.
		expect(esOscuro(null, true)).toBe(false);
	});
});
