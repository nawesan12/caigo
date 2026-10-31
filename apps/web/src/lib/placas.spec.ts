import { describe, expect, it } from 'vitest';
import { leerPedido, placasTexto } from './placas.ts';

const form = (o: Record<string, string>) => {
	const f = new FormData();
	for (const [k, v] of Object.entries(o)) f.set(k, v);
	return f;
};

describe('placas', () => {
	it('nombra el pedido en singular y plural', () => {
		expect(placasTexto({ cantidad: 1, tipo: 'comun' })).toBe('1 placa común');
		expect(placasTexto({ cantidad: 4, tipo: 'comun' })).toBe('4 placas comunes');
		expect(placasTexto({ cantidad: 2, tipo: 'ntag424' })).toBe('2 placas NTAG 424');
	});
	it('valida el formulario', () => {
		expect(leerPedido(form({ cantidad: '0' }))).toEqual({ error: 'Pedí de 1 a 20 placas.' });
		expect(leerPedido(form({ cantidad: '21' }))).toEqual({ error: 'Pedí de 1 a 20 placas.' });
		expect(leerPedido(form({ cantidad: '2.5' }))).toEqual({ error: 'Pedí de 1 a 20 placas.' });
		expect(leerPedido(form({ cantidad: '6', tipo: 'ntag424', nota: '  barra  ' }))).toEqual({
			cantidad: 6,
			tipo: 'ntag424',
			nota: 'barra'
		});
		expect(leerPedido(form({ cantidad: '1', tipo: 'otra' }))).toMatchObject({ tipo: 'comun' });
	});
});
