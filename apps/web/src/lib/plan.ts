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

/**
 * La línea de confianza de la tarjeta y el detalle: «Todos verificados · Organiza Martín (9/10) · Van
 * 2 chicas», con lo que da la API (DNI de todos, asistencia real de quien organiza y, sólo en planes
 * «Solo mujeres», cuántas van desde 2). Cada parte va con espacios duros: el renglón sólo se corta
 * entre partes, nunca deja una palabra sola.
 */
export function lineaConfianza(p: {
	organizador: { nombre: string; asistencia?: { fue: number; de: number } };
	confianza?: { todosVerificados: boolean; soloChicas: boolean; chicas?: number };
}): string[] {
	const c = p.confianza;
	const partes: string[] = [];
	if (c?.todosVerificados) partes.push('Todos verificados');
	const a = p.organizador.asistencia;
	const nombre = primerNombre(p.organizador.nombre);
	if (nombre) partes.push(`Organiza ${nombre}` + (a && a.de > 0 ? ` (${a.fue}/${a.de})` : ''));
	if (c?.chicas && c.chicas >= 2) partes.push(`Van ${c.chicas} chicas`);
	else if (c?.soloChicas) partes.push('Sólo chicas');
	return partes.map((x) => x.replace(/ /g, '\u00a0'));
}
