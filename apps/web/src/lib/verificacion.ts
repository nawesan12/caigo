import { partes } from './fechas.ts';
import type { Verificacion } from './tipos.ts';

/**
 * Verificada = teléfono + selfie + DNI aprobado (desde el 16/11 el DNI es obligatorio para usar
 * Caigo; la API responde 403 `falta_dni` a todo lo demás).
 */
export function verificada(v: Verificacion | null | undefined): boolean {
	return !!v && !!v.telefono && !!v.selfie && v.dni === 'aprobada';
}

/** En qué paso de /app/verificar está: 1 teléfono, 2 selfie, 3 DNI, 0 si ya terminó. */
export function pasoDe(v: Verificacion): 0 | 1 | 2 | 3 {
	if (!v.telefono) return 1;
	if (!v.selfie) return 2;
	if (v.dni !== 'aprobada') return 3;
	return 0;
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
