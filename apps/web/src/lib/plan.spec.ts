import { describe, expect, it } from 'vitest';
import {
	cobroTexto,
	faltanTexto,
	fotoDelPlan,
	iniciales,
	lineaConfianza,
	lista,
	primerNombre
} from './plan.ts';

describe('primerNombre', () => {
	it('una cuenta purgada no firma «Cuenta»', () => {
		expect(primerNombre('Nicolás Pérez')).toBe('Nicolás');
		expect(primerNombre('Cuenta borrada')).toBe('Cuenta borrada');
	});
});

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

describe('fotoDelPlan', () => {
	it('usa la del plan, después la del lugar y si no la de la categoría', () => {
		expect(fotoDelPlan({ foto: 'https://api/x', categoria: 'juego' })).toBe('https://api/x');
		expect(fotoDelPlan({ foto: '', categoria: 'musica', lugar: { foto: 'https://api/l' } })).toBe(
			'https://api/l'
		);
		expect(fotoDelPlan({ foto: '', categoria: 'gastronomia' })).toBe('/fotos/lugar.jpg');
		expect(fotoDelPlan({ categoria: 'cultura' })).toBe('');
	});
});

describe('lineaConfianza', () => {
	const sinCortes = (xs: string[]) => xs.map((x) => x.replace(/\u00a0/g, ' '));
	const org = { nombre: 'Martín Gómez', asistencia: { fue: 9, de: 10 } };
	it('todos verificados, quien organiza con su asistencia y cuántas chicas', () => {
		const l = lineaConfianza({
			organizador: org,
			confianza: { todosVerificados: true, soloChicas: true, chicas: 2 }
		});
		expect(sinCortes(l)).toEqual(['Todos verificados', 'Organiza Martín (9/10)', 'Van 2 chicas']);
		// Cada parte sin espacios comunes: no se corta en el medio.
		expect(l.every((x) => !x.includes(' '))).toBe(true);
	});
	it('sin asistencia todavía no muestra «(0/0)»; una sola chica no se cuenta', () => {
		const l = lineaConfianza({
			organizador: { nombre: 'Lu', asistencia: { fue: 0, de: 0 } },
			confianza: { todosVerificados: false, soloChicas: true }
		});
		expect(sinCortes(l)).toEqual(['Organiza Lu', 'Sólo chicas']);
	});
	it('una respuesta vieja sin confianza', () => {
		expect(sinCortes(lineaConfianza({ organizador: { nombre: 'Nico' } }))).toEqual([
			'Organiza Nico'
		]);
	});
});
