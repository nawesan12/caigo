import type { Logros, Plan } from './tipos.ts';

/** «Decidí rápido» aparece con al menos estos planes por decidir. */
export const MIN_DECIDIR = 8;

/** Un plan en el que todavía se puede decir «Caigo» (no estoy, no lo organizo y hay lugar). */
export function porDecidir(p: Plan): boolean {
	return (p.miEstado ?? 'ninguno') === 'ninguno' && p.faltan > 0 && !p.cancelado;
}

/**
 * Deslizar a la derecha suma directo sólo si no hay nada que confirmar: ingreso libre, gratis, sin
 * requisito de género. Con aprobación, entrada o seña, el gesto abre el detalle (ahí se confirma).
 */
export function sumaDirecto(p: Plan): boolean {
	return (
		porDecidir(p) &&
		p.ingreso === 'libre' &&
		(!p.lugar || p.cobro === 'gratis') &&
		(p.genero ?? 'cualquiera') === 'cualquiera'
	);
}

/**
 * Los planes para «Decidí rápido» (null si son pocos): sólo los que se suman con un toque. Los que
 * piden aprobación, entrada, seña o confirmar algo («Solo mujeres») se deciden en el detalle, con su
 * confirmación: en el mazo no hay dónde, y salir del mazo cortaba la racha de decisiones.
 */
export function paraDecidir(ps: Plan[]): Plan[] | null {
	const d = ps.filter(sumaDirecto);
	return d.length >= MIN_DECIDIR ? d : null;
}

/**
 * La línea de nivel del perfil: «Habitué · 9 con check-in». Con 0, sólo el nivel. «con check-in» y no
 * «planes»: en el mismo perfil está la cifra de planes a los que se anotó, que es otra cosa.
 */
export function nivelTexto(l: Logros): string {
	return l.planes > 0 ? `${l.nivel} · ${l.planes}\u00a0con\u00a0check-in` : l.nivel;
}

/** «Racha: 4 semanas», sólo desde 2 (una sola no es racha). El comodín se usa solo, sin anunciarlo. */
export function rachaTexto(l: Logros): string {
	if (!l.racha || l.racha < 2) return '';
	return `Racha: ${l.racha}\u00a0semanas`;
}

/**
 * El beneficio por nivel de un lugar, para quien mira: «Tenés: 10 % off» si ya es Habitué o más, o
 * «Con 3 planes más: 10 % off». Sin logros (sin sesión), el requisito.
 */
export function beneficioNivelTexto(beneficio: string, l?: Logros | null): string {
	if (!l) return `Habitué o más: ${beneficio}`;
	const falta = Math.max(HABITUE - l.planes, 0);
	if (falta === 0) return `Tenés: ${beneficio}`;
	return `Con ${falta === 1 ? '1 plan más' : `${falta} planes más`}: ${beneficio}`;
}

/** Planes con check-in desde los que se es Habitué (logros.Habitue). */
export const HABITUE = 5;

/** «Te faltan 6 para De fierro» (vacío en el último nivel). */
export function siguienteTexto(l: Logros): string {
	if (!l.siguiente || !l.faltan) return '';
	return `Te ${l.faltan === 1 ? 'falta 1' : `faltan ${l.faltan}`} para\u00a0${l.siguiente.replace(/ /g, '\u00a0')}`;
}

/** Evento de calendario (.ics) del plan, para «Agendar». */
export function ics(p: Plan, sitio: string, ahora = new Date()): string {
	const f = (d: Date) =>
		d
			.toISOString()
			.replace(/[-:]/g, '')
			.replace(/\.\d{3}/, '');
	const ini = new Date(p.empiezaEn);
	const fin = new Date(ini.getTime() + (p.duracionMin || 120) * 60_000);
	const esc = (s: string) => s.replace(/[\\;,]/g, (c) => '\\' + c).replace(/\n/g, '\\n');
	const lugar =
		p.direccion?.texto ||
		p.lugar?.nombre ||
		p.lugarNombre ||
		[p.zona, p.ciudad].filter(Boolean).join(', ');
	const url = `${sitio.replace(/\/+$/, '')}/p/${p.slug}`;
	return [
		'BEGIN:VCALENDAR',
		'VERSION:2.0',
		'PRODID:-//Caigo//Planes//ES',
		'BEGIN:VEVENT',
		`UID:${p.id}@caigo.com.ar`,
		`DTSTAMP:${f(ahora)}`,
		`DTSTART:${f(ini)}`,
		`DTEND:${f(fin)}`,
		`SUMMARY:${esc(p.titulo)}`,
		`LOCATION:${esc(lugar)}`,
		`URL:${url}`,
		`DESCRIPTION:${esc(url)}`,
		'END:VEVENT',
		'END:VCALENDAR'
	].join('\r\n');
}
