import { partes } from './fechas.ts';
import type { Verificacion } from './tipos.ts';

/**
 * Verificada = DNI aprobado (frente, dorso y la selfie con prueba de vida, que queda como la de la
 * cuenta). Sin DNI se mira Caigo; para sumarse, crear o chatear la API responde 403 `falta_dni`.
 * El teléfono es opcional desde el 18/11.
 */
export function verificada(v: Verificacion | null | undefined): boolean {
	return !!v && v.dni === 'aprobada';
}

/** En qué paso de /app/verificar está: 2 selfie, 3 DNI, 0 si ya terminó (el 1, el teléfono, ya no se pide). */
export function pasoDe(v: Verificacion): 0 | 1 | 2 | 3 {
	if (v.dni === 'aprobada') return 0;
	if (!v.selfie) return 2;
	return 3;
}

/** Para qué se pide el DNI (el título de la primera lámina de /app/verificar). */
export type MotivoDni = 'sumarte' | 'crear' | 'chatear' | '';

export const TITULO_DNI: Record<MotivoDni, string> = {
	sumarte: 'Para sumarte, verificamos que seas\u00a0vos',
	crear: 'Para armar tu plan, verificamos que seas\u00a0vos',
	chatear: 'Para chatear, verificamos que seas\u00a0vos',
	'': 'Antes, verificamos que seas\u00a0vos'
};

/** El motivo según la ruta de /app que pidió la verificación. */
export function motivoDni(rutaId: string): MotivoDni {
	if (rutaId === '/app/crear' || rutaId.startsWith('/app/plan/[id]/editar')) return 'crear';
	if (rutaId.includes('/chat') || rutaId.startsWith('/app/mensajes')) return 'chatear';
	if (rutaId === '/app/plan/[id]' || rutaId.startsWith('/p/')) return 'sumarte';
	return '';
}

/** La fecha YYYY-MM-DD si existe de verdad (2005-02-31 no); si no, null. */
export function fechaValida(s: string): [number, number, number] | null {
	if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) return null;
	const [a, m, d] = s.split('-').map(Number);
	const f = new Date(Date.UTC(a, m - 1, d));
	if (f.getUTCFullYear() !== a || f.getUTCMonth() !== m - 1 || f.getUTCDate() !== d) return null;
	return [a, m, d];
}

/** Años cumplidos hoy (en Argentina) de alguien nacido en `nac`. */
export function edad(nac: [number, number, number], ahora = new Date()): number {
	const [a, m, d] = nac;
	// El servidor corre en UTC: de 21 a 24 h ya sería mañana y alguien cumplía un día antes.
	const hoy = partes(ahora);
	let e = hoy.anio - a;
	if (hoy.mes + 1 < m || (hoy.mes + 1 === m && hoy.dia < d)) e--;
	return e;
}
