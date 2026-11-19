import { describe, expect, it } from 'vitest';
import { filasPlus, textoFino } from './plus.ts';

const nb = ' ';

describe('Plus', () => {
	it('texto chico igual que las apps', () => {
		expect(textoFino('semana', '$2.500', 0, 'Mercado Pago')).toBe(
			`$2.500 por${nb}semana, se renueva sola. Cancelás cuando quieras desde${nb}Mercado${nb}Pago.`
		);
		expect(textoFino('mes', '$6.900', 7, 'Mercado Pago')).toBe(
			`Gratis 7${nb}días, después $6.900 por${nb}mes, se renueva sola. Cancelás cuando quieras desde${nb}Mercado${nb}Pago.`
		);
		expect(textoFino('mes', '$6.900', 0, 'Mercado Pago').startsWith(`$6.900 por${nb}mes`)).toBe(
			true
		);
	});
	it('las cinco filas de las apps, sin la de seguridad', () => {
		const f = filasPlus();
		expect(f).toHaveLength(5);
		expect(f.map((x) => x[0])).not.toContain('Sólo chicas');
		expect(f[4][0]).toBe('Filtros de edad, cupo y precio');
	});
});
