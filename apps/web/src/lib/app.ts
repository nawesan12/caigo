/**
 * Caigo desde la web (/app): textos y cuentas de las pantallas de Mockups v5.
 */
import { partes, diasHasta } from './fechas.ts';
import type { Plan } from './tipos.ts';

/** Centro de Mar del Plata (sin permiso de ubicación). */
export const CENTRO_MDP: [number, number] = [-38.0055, -57.5426];

/** Cookie (no httpOnly: la pone el navegador) con la última ubicación: "lat,lng". */
export const COOKIE_UBIC = 'caigo_ubic';

/** "lat,lng" → [lat, lng] si es una coordenada válida. */
export function leerUbicacion(v: string | null | undefined): [number, number] | null {
	const m = /^(-?\d{1,2}(?:\.\d+)?),(-?\d{1,3}(?:\.\d+)?)$/.exec(v ?? '');
	if (!m) return null;
	const lat = +m[1],
		lng = +m[2];
	if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null;
	return [lat, lng];
}

/** "800 m" · "1,2 km" · "12 km" */
export function distancia(m: number | null | undefined): string {
	if (m == null) return '';
	if (m < 1000) return `${Math.max(100, Math.round(m / 100) * 100)} m`;
	const km = m / 1000;
	return km < 10 ? `${km.toFixed(1).replace('.', ',')} km` : `${Math.round(km)} km`;
}

/** Color de fondo de la foto mientras carga (o si el plan no tiene), como los tint del handoff. */
export const TINTES: Record<string, string> = {
	deporte: '#3B6B57',
	juego: '#5B43C9',
	previa: '#3A3452',
	salida: '#3A3452',
	cita_doble: '#7456F1',
	cultura: '#5B43C9',
	gastronomia: '#3B6B57',
	otro: '#5B43C9',
	taller: '#5B43C9',
	musica: '#3A3452',
	fiesta: '#3A3452'
};

/** Las categorías que se eligen al crear un plan (06a), en ese orden. */
export const CATEGORIAS_CREAR: { valor: string; etiqueta: string }[] = [
	{ valor: 'deporte', etiqueta: 'Deporte' },
	{ valor: 'juego', etiqueta: 'Juego' },
	{ valor: 'previa', etiqueta: 'Previa' },
	{ valor: 'salida', etiqueta: 'Salida' },
	{ valor: 'cita_doble', etiqueta: 'Cita doble' },
	{ valor: 'cultura', etiqueta: 'Cultura' },
	{ valor: 'gastronomia', etiqueta: 'Gastronomía' },
	{ valor: 'otro', etiqueta: 'Otro' }
];

/** Los filtros de Ahora (02): Todos, Deporte, Juego, Previa, Cita doble, Salida. */
export const FILTROS_AHORA: { valor: string; etiqueta: string }[] = [
	{ valor: '', etiqueta: 'Todos' },
	{ valor: 'deporte', etiqueta: 'Deporte' },
	{ valor: 'juego', etiqueta: 'Juego' },
	{ valor: 'previa', etiqueta: 'Previa' },
	{ valor: 'cita_doble', etiqueta: 'Cita doble' },
	{ valor: 'salida', etiqueta: 'Salida' }
];

/** "Hoy, 20:30 a 21:30" (detalle del plan). */
export function horario(empieza: string, durMin: number, ahora: Date = new Date()): string {
	const ini = partes(empieza);
	const fin = partes(new Date(new Date(empieza).getTime() + durMin * 60000));
	const n = diasHasta(empieza, ahora);
	const dia =
		n === 0
			? 'Hoy'
			: n === 1
				? 'Mañana'
				: `${['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'][ini.semana]} ${ini.dia}`;
	return `${dia}, ${ini.hora} a ${fin.hora}`;
}

/** El chip coral de "Ahora" del detalle: "Ahora" si ya arrancó, "Ahora · en 2 h" si falta poco. */
export function chipAhora(empieza: string, ahora: Date = new Date()): string | null {
	const min = (new Date(empieza).getTime() - ahora.getTime()) / 60000;
	if (min > 180) return null;
	if (min <= 5) return 'Ahora';
	if (min < 60) return `Ahora · en ${Math.round(min)} min`;
	return `Ahora · en ${Math.round(min / 60)} h`;
}

/** "Fue a 23 de 24 planes · 96% asistencia" o, sin historial, el texto de alguien nuevo. */
export function reputacion(a: { fue: number; de: number } | undefined): string {
	if (!a || a.de === 0) return 'Nuevo en Caigo · todavía sin planes';
	return `Fue a ${a.fue} de ${a.de} planes · ${Math.round((a.fue / a.de) * 100)}% asistencia`;
}

/** Grupos de "Mis planes": HOY, ESTA SEMANA y MÁS ADELANTE (o PASADOS en historial). */
export function agrupar(planes: Plan[], historial: boolean, ahora: Date = new Date()) {
	if (historial) return planes.length ? [{ titulo: 'PASADOS', planes }] : [];
	const g = new Map<string, Plan[]>();
	for (const p of planes) {
		const n = diasHasta(p.empiezaEn, ahora);
		const t = n <= 0 ? 'HOY' : n < 7 ? 'ESTA SEMANA' : 'MÁS ADELANTE';
		g.set(t, [...(g.get(t) ?? []), p]);
	}
	return ['HOY', 'ESTA SEMANA', 'MÁS ADELANTE']
		.filter((t) => g.has(t))
		.map((titulo) => ({ titulo, planes: g.get(titulo)! }));
}

/** El badge de un plan en Mis planes: "Faltan 2" o, si ya está completo, "Ya somos 4". */
export function badgeMio(p: Pick<Plan, 'faltan' | 'somos'>): { texto: string; completo: boolean } {
	if (p.faltan <= 0) return { texto: `Ya somos ${p.somos}`, completo: true };
	return { texto: p.faltan === 1 ? 'Falta 1' : `Faltan ${p.faltan}`, completo: false };
}

/** "+54 9 223 555 0142" → lo que se manda (la API normaliza el resto). */
export function telefonoAR(t: string): string {
	const d = t.replace(/\D/g, '');
	return d.startsWith('54') ? `+${d}` : d;
}
