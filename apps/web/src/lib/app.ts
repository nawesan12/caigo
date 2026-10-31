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

/** "Le copa": lo que se sugiere elegir (LIKES del Prototipo). */
export const INTERESES_SUGERIDOS = [
	'Fútbol 5',
	'Pádel',
	'Truco',
	'Previas',
	'After beach',
	'Surf',
	'Cine',
	'Asado',
	'Running',
	'Juegos de mesa',
	'Música en vivo',
	'Mate en la playa'
];

/** Las preguntas del perfil (prompts del Prototipo). */
export const PREGUNTAS = [
	'Mi domingo ideal',
	'Te gano en…',
	'Un plan que nunca falla',
	'Me sumo seguro si…',
	'Mi plan ideal',
	'Lo más espontáneo que hice',
	'Lo que más me gusta de Mar del Plata',
	'Mi talento oculto'
];

/** Qué busca en Caigo (INTENTS del Prototipo → `intenciones` de la API). */
export const INTENCIONES: { valor: string; etiqueta: string; corto: string; texto: string }[] = [
	{
		valor: 'planes',
		etiqueta: 'Sumarme a planes',
		corto: 'Planes',
		texto: 'Partidos, previas y mesas a las que les falta uno'
	},
	{
		valor: 'gente',
		etiqueta: 'Conocer gente nueva',
		corto: 'Gente nueva',
		texto: 'Personas de a una con ganas de salir'
	},
	{
		valor: 'completar',
		etiqueta: 'Completar mis planes',
		corto: 'Organizar',
		texto: 'Ya tengo el plan, me falta gente'
	},
	{
		valor: 'citas',
		etiqueta: 'Algo más romántico',
		corto: 'Citas',
		texto: 'Citas, citas dobles y ver qué onda'
	}
];

/** Zonas de respaldo si la API todavía no las da (GET /v1/publico/zonas). */
export const ZONAS_FIJAS: Record<string, string[]> = {
	'Mar del Plata': [
		'Güemes',
		'Centro',
		'La Perla',
		'Playa Grande',
		'Punta Mogotes',
		'Constitución',
		'Los Troncos',
		'Chauvín'
	],
	'Villa Gesell': ['Centro', 'Norte', 'Sur', 'Mar de las Pampas']
};

/** Las fechas que se eligen en los filtros de Explorar. */
export const FECHAS_EXPLORAR: { valor: string; etiqueta: string }[] = [
	{ valor: 'hoy', etiqueta: 'Hoy' },
	{ valor: 'manana', etiqueta: 'Mañana' },
	{ valor: 'finde', etiqueta: 'Este finde' }
];

/** YYYY-MM-DD de un día en Argentina, `n` días después de `ahora`. */
function diaAR(ahora: Date, n: number): string {
	const p = partes(new Date(ahora.getTime() + n * 86400_000));
	return `${p.anio}-${String(p.mes + 1).padStart(2, '0')}-${String(p.dia).padStart(2, '0')}`;
}

/**
 * El rango (RFC 3339, hora de Argentina) de un filtro de fecha de Explorar: hoy, mañana, el finde
 * (sábado y domingo; si ya es finde, lo que queda) o un día YYYY-MM-DD. Null si no es válido.
 */
export function rangoFecha(
	fecha: string,
	ahora: Date = new Date()
): { desde: string; hasta: string; dias: string[] } | null {
	const rango = (desde: number, cuantos: number) => {
		const dias = Array.from({ length: cuantos }, (_, i) => diaAR(ahora, desde + i));
		return {
			desde: `${dias[0]}T00:00:00-03:00`,
			hasta: `${diaAR(ahora, desde + cuantos)}T00:00:00-03:00`,
			dias
		};
	};
	if (fecha === 'hoy') return rango(0, 1);
	if (fecha === 'manana') return rango(1, 1);
	if (fecha === 'finde') {
		const s = partes(ahora).semana;
		if (s === 0) return rango(0, 1);
		return rango(6 - s, 2);
	}
	const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(fecha);
	if (!m) return null;
	for (let i = 0; i <= 60; i++) if (diaAR(ahora, i) === fecha) return rango(i, 1);
	return null;
}

/** Une las dos últimas palabras con un espacio duro: un título dinámico nunca deja una sola abajo. */
export function sinViuda(t: string): string {
	return t.trim().replace(/\s+(\S+)$/, ' $1');
}
