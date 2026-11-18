import { describe, expect, it } from 'vitest';
import { aplicarModerado, avisoPlan, lineaMensaje, type Moderacion } from './moderacion.ts';

type M = { id: number; texto: string; moderacion?: Moderacion };
const lista: M[] = [
	{ id: 1, texto: 'hola' },
	{ id: 2, texto: 'feo' },
	{ id: 3, texto: 'chau' }
];

describe('aplicarModerado', () => {
	it('un ajeno oculto sale de la lista', () => {
		const r = aplicarModerado(lista, { id: 2, texto: 'feo', moderacion: 'oculto' }, false);
		expect(r.map((m) => m.id)).toEqual([1, 3]);
	});
	it('uno propio oculto queda, con su estado', () => {
		const r = aplicarModerado(lista, { id: 2, texto: 'feo', moderacion: 'oculto' }, true);
		expect(r[1].moderacion).toBe('oculto');
		expect(r).toHaveLength(3);
	});
	it('en revisión o vuelto a la normalidad: se reemplaza', () => {
		const r = aplicarModerado(lista, { id: 2, texto: 'feo', moderacion: 'revision' }, false);
		expect(r[1].moderacion).toBe('revision');
		expect(aplicarModerado(r, { id: 2, texto: 'feo', moderacion: '' }, true)[1].moderacion).toBe(
			''
		);
	});
	it('uno que no está no se agrega', () => {
		expect(aplicarModerado(lista, { id: 9, texto: 'x', moderacion: '' }, true)).toBe(lista);
	});
});

describe('textos', () => {
	it('la línea bajo la burbuja', () => {
		expect(lineaMensaje({})).toBe('');
		expect(lineaMensaje({ moderacion: 'revision' })).toBe('En revisión');
		expect(lineaMensaje({ moderacion: 'oculto' })).toBe('Oculto por las normas');
	});
	it('el aviso del plan, sólo para quien organiza', () => {
		expect(avisoPlan({ miEstado: 'aceptada', moderacion: 'oculto' })).toBeNull();
		expect(avisoPlan({ miEstado: 'organizador', moderacion: '' })).toBeNull();
		expect(avisoPlan({ miEstado: 'organizador', moderacion: 'revision' })?.oculto).toBe(false);
		expect(avisoPlan({ miEstado: 'organizador', moderacion: 'oculto' })?.texto).toContain(
			'nadie más'
		);
	});
});
