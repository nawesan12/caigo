import { describe, expect, it } from 'vitest';
import type { RequestEvent } from '@sveltejs/kit';
import { soloMira } from './cuenta.ts';

const evento = (ruta: string, metodo = 'GET', accion = false) =>
	({
		route: { id: ruta },
		request: new Request('http://localhost' + ruta, {
			method: metodo,
			headers: accion ? { 'x-sveltekit-action': 'true' } : {}
		})
	}) as unknown as RequestEvent;

describe('soloMira (sin DNI)', () => {
	it('deja mirar «Esta semana», el detalle y los perfiles', () => {
		for (const r of ['/app/semana', '/app', '/app/plan/[id]', '/app/persona/[id]', '/app/mis']) {
			expect(soloMira(evento(r)), r).toBe(true);
		}
	});
	it('no deja hacer nada (acciones, POST) ni entrar a lo demás', () => {
		expect(soloMira(evento('/app/semana', 'POST'))).toBe(false);
		expect(soloMira(evento('/app/plan/[id]', 'GET', true))).toBe(false);
		expect(soloMira(evento('/app/crear'))).toBe(false);
		expect(soloMira(evento('/app/chat/[id]'))).toBe(false);
	});
});
