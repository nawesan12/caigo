/** Textos del plan como los muestra el handoff. */

/** f === 0 → "Completo" (lima), f === 1 → "Falta 1", si no "Faltan N" (coral). */
export function faltanTexto(f: number): string {
	if (f <= 0) return 'Completo';
	return f === 1 ? 'Falta 1' : `Faltan ${f}`;
}

/** Las categorías de la API → cómo se leen (las del handoff). */
export const CATEGORIAS: Record<string, string> = {
	deporte: 'Deporte',
	juego: 'Juego',
	previa: 'Previa',
	salida: 'Salida',
	cita_doble: 'Cita doble',
	cultura: 'Cultura',
	gastronomia: 'Gastronomía',
	otro: 'Otro',
	taller: 'Taller',
	musica: 'Música en vivo',
	fiesta: 'Fiesta'
};

/** Sin foto propia ni del lugar, la de su categoría (las de los tiles de Explorar, como en las apps). */
export const FOTOS_CATEGORIA: Record<string, string> = {
	deporte: '/fotos/playa.jpg',
	juego: '/fotos/truco.jpg',
	previa: '/fotos/previa.jpg',
	salida: '/fotos/grupo.jpg',
	cita_doble: '/fotos/amigos.jpg',
	gastronomia: '/fotos/lugar.jpg',
	fiesta: '/fotos/previa.jpg',
	musica: '/fotos/lugar.jpg'
};

/** La foto que se muestra de un plan: la suya, la del lugar o la de la categoría ('' si no hay). */
export function fotoDelPlan(p: {
	foto?: string | null;
	categoria: string;
	lugar?: { foto?: string } | null;
}): string {
	return p.foto || p.lugar?.foto || FOTOS_CATEGORIA[p.categoria] || '';
}

/** Las del panel de lugares, en el orden del handoff ("Juegos" es `juego`). */
export const CATEGORIAS_LUGAR: { valor: string; etiqueta: string }[] = [
	{ valor: 'taller', etiqueta: 'Taller' },
	{ valor: 'musica', etiqueta: 'Música en vivo' },
	{ valor: 'juego', etiqueta: 'Juegos' },
	{ valor: 'deporte', etiqueta: 'Deporte' },
	{ valor: 'gastronomia', etiqueta: 'Gastronomía' },
	{ valor: 'fiesta', etiqueta: 'Fiesta' }
];

/** "Nico, Lu y Cami" */
export function lista(nombres: string[]): string {
	const n = nombres.filter(Boolean);
	if (n.length <= 1) return n[0] ?? '';
	return n.slice(0, -1).join(', ') + ' y ' + n[n.length - 1];
}

/**
 * El primer nombre ("Nicolás Pérez" → "Nicolás"). Una cuenta purgada se llama «Cuenta borrada»
 * (así firma en los chats): queda entera, no «Cuenta».
 */
export function primerNombre(n: string): string {
	const t = (n ?? '').trim();
	if (t === 'Cuenta borrada') return t;
	return t.split(/\s+/)[0] ?? '';
}

/** Iniciales para el avatar del lugar ("Café Molienda" → "CM"). */
export function iniciales(n: string): string {
	return (n ?? '')
		.trim()
		.split(/\s+/)
		.slice(0, 2)
		.map((p) => p[0]?.toUpperCase() ?? '')
		.join('');
}

/** El chip de cobro del panel: "Gratis", "Entrada $4.500", "Seña 30% · $8.000". */
export function cobroTexto(p: {
	cobro: string;
	precioCentavos?: number | null;
	senaPct?: number | null;
}): string {
	const precio =
		'$' + new Intl.NumberFormat('es-AR').format(Math.round((p.precioCentavos ?? 0) / 100));
	if (p.cobro === 'sena') return `Seña ${p.senaPct ?? 0}% · ${precio}`;
	if (p.cobro === 'entrada') return `Entrada ${precio}`;
	return 'Gratis';
}

/** La asistencia, igual en todas las pantallas: «fue a 9 de 10» (sin porcentaje, no es un puntaje). */
export function asistenciaTexto(a: { fue: number; de: number } | undefined): string {
	if (!a || a.de <= 0) return '';
	return `fue a ${a.fue} de ${a.de}`;
}

/**
 * La línea de confianza, sólo con lo que distingue a un plan (si no hay nada, ninguna). En la tarjeta:
 * «Martín fue a 9 de 10 · Anfitrión · Plan de chicas»; de un lugar aliado, «Organiza La Fonda». En el
 * detalle (`detalle`), quien organiza ya tiene su fila: sólo «Todos verificados · Plan de chicas», que
 * con el DNI obligatorio casi siempre es cierto y por eso no va en la tarjeta. Cada parte con espacios
 * duros: el renglón sólo se corta entre partes.
 */
export function lineaConfianza(
	p: {
		organizador: { nombre: string; asistencia?: { fue: number; de: number }; anfitrion?: boolean };
		lugar?: { nombre: string } | null;
		confianza?: { todosVerificados: boolean; soloChicas: boolean };
	},
	opciones: { detalle?: boolean } = {}
): string[] {
	const c = p.confianza;
	const partes: string[] = [];
	if (opciones.detalle) {
		if (c?.todosVerificados) partes.push('Todos verificados');
	} else if (p.lugar?.nombre) {
		partes.push(`Organiza ${p.lugar.nombre}`);
	} else {
		const nombre = primerNombre(p.organizador.nombre);
		const a = asistenciaTexto(p.organizador.asistencia);
		if (nombre && a) partes.push(`${nombre} ${a}`);
		if (p.organizador.anfitrion) partes.push('Anfitrión');
	}
	if (c?.soloChicas) partes.push('Plan de chicas');
	return partes.map((x) => x.replace(/ /g, '\u00a0'));
}
