/**
 * Moderación automática (docs/api.md, «Moderación automática»). La API manda `moderacion` sólo a
 * quien escribió el mensaje u organiza el plan: al resto, lo oculto directamente no le llega.
 */
export type Moderacion = '' | 'revision' | 'oculto';

/**
 * Aplica un evento `moderado` a la lista del chat. Si se ocultó un mensaje ajeno, sale de la lista
 * (para el resto ya no existe); si es mío, se reemplaza para mostrar el estado. Un mensaje que no
 * está en la lista no se agrega: puede ser de otra conversación o de una página que no se trajo.
 */
export function aplicarModerado<T extends { id: number; moderacion?: Moderacion }>(
	lista: T[],
	m: T,
	esMio: boolean
): T[] {
	if (!lista.some((x) => x.id === m.id)) return lista;
	if (m.moderacion === 'oculto' && !esMio) return lista.filter((x) => x.id !== m.id);
	return lista.map((x) => (x.id === m.id ? m : x));
}

/** La línea chica bajo una burbuja propia. */
export function lineaMensaje(m: { moderacion?: Moderacion }): string {
	if (m.moderacion === 'revision') return 'En revisión';
	if (m.moderacion === 'oculto') return 'Oculto por las normas';
	return '';
}

/** El aviso para quien organiza un plan que el equipo está revisando u ocultó. */
export function avisoPlan(p: {
	miEstado?: string;
	moderacion?: Moderacion;
}): { texto: string; oculto: boolean } | null {
	if (p.miEstado !== 'organizador') return null;
	if (p.moderacion === 'revision') return { texto: 'Tu plan está en revisión.', oculto: false };
	if (p.moderacion === 'oculto')
		// Espacio duro: «lo ve» no queda solo en el último renglón.
		return { texto: 'Tu plan está oculto por las normas: nadie más lo ve.', oculto: true };
	return null;
}
