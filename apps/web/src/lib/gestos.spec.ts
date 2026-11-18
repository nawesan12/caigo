import { describe, expect, it } from 'vitest';
import {
	beneficioNivelTexto,
	ics,
	MIN_DECIDIR,
	nivelTexto,
	paraDecidir,
	rachaTexto,
	siguienteTexto,
	sumaDirecto
} from './gestos.ts';
import type { Logros, Plan } from './tipos.ts';

const plan = (x: Partial<Plan> = {}): Plan =>
	({
		id: 'p1',
		slug: 'truco-guemes-k3f9',
		titulo: 'Truco, en Güemes',
		categoria: 'juego',
		descripcion: '',
		empiezaEn: '2026-11-20T00:30:00Z',
		duracionMin: 90,
		ciudad: 'Mar del Plata',
		zona: 'Güemes',
		lugarNombre: 'La Fonda',
		somos: 3,
		total: 4,
		faltan: 1,
		ingreso: 'libre',
		soloVerificados: true,
		genero: 'cualquiera',
		nivel: '',
		foto: '',
		cancelado: false,
		organizador: { id: 'o', nombre: 'Martín', verificada: true },
		van: [],
		miEstado: 'ninguno',
		cobro: 'gratis',
		aceptaGrupos: true,
		...x
	}) as Plan;

describe('sumaDirecto', () => {
	it('sólo planes libres, gratis y sin requisito de género', () => {
		expect(sumaDirecto(plan())).toBe(true);
		expect(sumaDirecto(plan({ ingreso: 'aprobacion' }))).toBe(false);
		expect(sumaDirecto(plan({ genero: 'mujeres' }))).toBe(false);
		expect(
			sumaDirecto(plan({ lugar: { id: 'l', slug: 'l', nombre: 'L', zona: '' }, cobro: 'entrada' }))
		).toBe(false);
		expect(sumaDirecto(plan({ faltan: 0 }))).toBe(false);
		expect(sumaDirecto(plan({ miEstado: 'aceptada' }))).toBe(false);
		expect(sumaDirecto(plan({ miEstado: 'organizador' }))).toBe(false);
	});
});

describe('paraDecidir', () => {
	it('aparece con 8 o más por decidir', () => {
		const ps = Array.from({ length: MIN_DECIDIR - 1 }, (_, i) => plan({ id: `p${i}` }));
		expect(paraDecidir(ps)).toBeNull();
		expect(paraDecidir([...ps, plan({ id: 'x', miEstado: 'aceptada' })])).toBeNull();
		expect(paraDecidir([...ps, plan({ id: 'y' })])?.length).toBe(MIN_DECIDIR);
	});
});

describe('textos de logros', () => {
	const l: Logros = {
		nivel: 'Habitué',
		planes: 9,
		siguiente: 'De fierro',
		faltan: 6,
		racha: 4,
		comodin: true,
		insignias: [],
		anfitrion: false
	};
	it('cabecera, racha y siguiente', () => {
		const sin = (x: string) => x.replace(/\u00a0/g, ' ');
		expect(sin(nivelTexto(l))).toBe('Habitué · 9 con check-in');
		expect(nivelTexto({ ...l, nivel: 'Nuevo', planes: 0 })).toBe('Nuevo');
		expect(sin(rachaTexto(l))).toBe('Racha: 4 semanas');
		expect(beneficioNivelTexto('10 % off', l)).toBe('Tenés: 10 % off');
		expect(beneficioNivelTexto('10 % off', { ...l, planes: 2 })).toBe('Con 3 planes más: 10 % off');
		expect(beneficioNivelTexto('10 % off', null)).toBe('Habitué o más: 10 % off');
		expect(rachaTexto({ ...l, racha: 1 })).toBe('');
		expect(rachaTexto({ ...l, comodin: false })).toBe('Racha: 4 semanas');
		expect(siguienteTexto(l)).toBe('Te faltan 6 para De fierro');
		expect(siguienteTexto({ ...l, faltan: 1 })).toBe('Te falta 1 para De fierro');
		expect(siguienteTexto({ ...l, siguiente: undefined, faltan: 0 })).toBe('');
	});
});

describe('ics', () => {
	it('arma el evento con hora de fin y link', () => {
		const t = ics(plan(), 'https://caigo.com.ar/', new Date('2026-11-18T12:00:00Z'));
		expect(t).toContain('DTSTART:20261120T003000Z');
		expect(t).toContain('DTEND:20261120T020000Z');
		expect(t).toContain('SUMMARY:Truco\\, en Güemes');
		expect(t).toContain('URL:https://caigo.com.ar/p/truco-guemes-k3f9');
		expect(t).toContain('LOCATION:La Fonda');
	});
});
