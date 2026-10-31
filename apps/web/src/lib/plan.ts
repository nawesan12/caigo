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

/** El primer nombre ("Nicolás Pérez" → "Nicolás"). */
export function primerNombre(n: string): string {
	return (n ?? '').trim().split(/\s+/)[0] ?? '';
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
