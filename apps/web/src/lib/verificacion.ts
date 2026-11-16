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
