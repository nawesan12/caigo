import { describe, expect, it } from 'vitest';
import { planPublico } from './publico.ts';
import { planDePrueba } from '#lib/prueba/plan.ts';

describe('plan sin sesión', () => {
	it('sólo primer nombre y foto: sin ids, edad ni apellido', () => {
		const p = planDePrueba({
			organizador: { id: 'u-org', nombre: 'Nahuel Santillán', edad: 31, verificada: true },
			van: [
				{ id: 'u-1', nombre: 'Lucía Pérez', edad: 27, foto: '/f.jpg', verificada: false },
				{ id: 'u-2', nombre: 'Cami', verificada: true }
			]
		});
		const json = JSON.stringify(planPublico(p));
		expect(json).not.toContain('u-org');
		expect(json).not.toContain('u-1');
		expect(json).not.toContain('Santillán');
		expect(json).not.toContain('Pérez');
		expect(json).not.toContain('"edad"');
		const q = planPublico(p);
		expect(q.van.map((v) => v.nombre)).toEqual(['Lucía', 'Cami']);
		expect(q.van[0].foto).toBe('/f.jpg');
		expect(q.organizador.nombre).toBe('Nahuel');
	});
});
