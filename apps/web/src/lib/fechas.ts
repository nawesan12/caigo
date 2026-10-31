/**
 * Fechas en hora de Argentina, con el formato del handoff: "Hoy 19:30", "Jue 30 · 21:00".
 * Argentina no tiene horario de verano: UTC−3 fijo.
 */

const ZONA = 'America/Argentina/Buenos_Aires';
const OFFSET_MS = -3 * 3600_000;
const DIAS = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];
const DIAS_LARGOS = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
const MESES = [
	'enero',
	'febrero',
	'marzo',
	'abril',
	'mayo',
	'junio',
	'julio',
	'agosto',
	'septiembre',
	'octubre',
	'noviembre',
	'diciembre'
];

/** Las partes de una fecha vista desde Argentina. */
export function partes(f: Date | string) {
	const d = new Date(new Date(f).getTime() + OFFSET_MS);
	return {
		anio: d.getUTCFullYear(),
		mes: d.getUTCMonth(),
		dia: d.getUTCDate(),
		semana: d.getUTCDay(),
		hora: String(d.getUTCHours()).padStart(2, '0') + ':' + String(d.getUTCMinutes()).padStart(2, '0')
	};
}

/** Días de diferencia entre dos fechas (en calendario de Argentina). */
function diasEntre(a: Date | string, b: Date | string): number {
	const pa = partes(a),
		pb = partes(b);
	return Math.round(
		(Date.UTC(pb.anio, pb.mes, pb.dia) - Date.UTC(pa.anio, pa.mes, pa.dia)) / 86400_000
	);
}

/** "Hoy 19:30" · "Mañana 21:00" · "Jue 30 · 21:00" (chips del panel). */
export function cuandoCorto(f: Date | string, ahora: Date = new Date()): string {
	const p = partes(f);
	const n = diasEntre(ahora, f);
	if (n === 0) return `Hoy ${p.hora}`;
	if (n === 1) return `Mañana ${p.hora}`;
	return `${DIAS[p.semana]} ${p.dia} · ${p.hora}`;
}

/** "Hoy, 21:30" · "Mañana, 21:30" · "Jueves 30 de octubre, 21:30" (link público). */
export function cuandoLargo(f: Date | string, ahora: Date = new Date()): string {
	const p = partes(f);
	const n = diasEntre(ahora, f);
	if (n === 0) return `Hoy, ${p.hora}`;
	if (n === 1) return `Mañana, ${p.hora}`;
	return `${DIAS_LARGOS[p.semana]} ${p.dia} de ${MESES[p.mes]}, ${p.hora}`;
}

/** "Martes 28 de octubre" (encabezado del panel). */
export function fechaDeHoy(ahora: Date = new Date()): string {
	const p = partes(ahora);
	return `${DIAS_LARGOS[p.semana]} ${p.dia} de ${MESES[p.mes]}`;
}

/** Si una fecha cae hoy en Argentina. */
export function esHoy(f: Date | string, ahora: Date = new Date()): boolean {
	return diasEntre(ahora, f) === 0;
}

/** Días hasta una fecha (negativo si ya pasó). */
export function diasHasta(f: Date | string, ahora: Date = new Date()): number {
	return diasEntre(ahora, f);
}

/**
 * Los días que ofrece el paso "Cuándo y cupo": Hoy, Mañana y los tres siguientes ("Sáb 31").
 * `valor` es la fecha en Argentina (YYYY-MM-DD).
 */
export function diasParaElegir(ahora: Date = new Date(), cuantos = 5) {
	const out: { etiqueta: string; valor: string }[] = [];
	for (let i = 0; i < cuantos; i++) {
		const d = new Date(ahora.getTime() + i * 86400_000);
		const p = partes(d);
		const valor = `${p.anio}-${String(p.mes + 1).padStart(2, '0')}-${String(p.dia).padStart(2, '0')}`;
		out.push({
			etiqueta: i === 0 ? 'Hoy' : i === 1 ? 'Mañana' : `${DIAS[p.semana]} ${p.dia}`,
			valor
		});
	}
	return out;
}

/** "2026-10-31" + "19:30" en Argentina → RFC 3339 con −03:00. */
export function aRFC3339(dia: string, hora: string): string {
	return `${dia}T${hora}:00-03:00`;
}

export { ZONA };
