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

/** Los planes para «Decidí rápido»: los de la lista que quedan por decidir (null si son pocos). */
export function paraDecidir(ps: Plan[]): Plan[] | null {
	const d = ps.filter(porDecidir);
	return d.length >= MIN_DECIDIR ? d : null;
}

/** La línea de la cabecera del perfil: «Habitué · 9 planes». */
export function nivelTexto(l: Logros): string {
	return `${l.nivel} · ${l.planes} ${l.planes === 1 ? 'plan' : 'planes'}`;
}

/** «Racha: 4 semanas · comodín libre», sólo desde 2 semanas (una sola no es racha). */
export function rachaTexto(l: Logros): string {
	if (!l.racha || l.racha < 2) return '';
	return `Racha: ${l.racha}\u00a0semanas${l.comodin ? ' · comodín\u00a0libre' : ''}`;
}

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
