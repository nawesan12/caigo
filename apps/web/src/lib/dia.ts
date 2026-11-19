/**
 * El día del plan y después (informe del 18/11): cuándo se muestra «Hoy», la cuenta regresiva, la
 * víspera, las caritas y el ritual de los lunes. Todo en hora de Argentina.
 */
import { minutosHasta } from './app.ts';
import { diasHasta, partes } from './fechas.ts';

const HORA = 3600_000;

/**
 * «Hoy» (cuenta regresiva, Llegué, ¿Todo bien?) va desde 3 h antes hasta 2 h después del final: la
 * vuelta también es parte del plan («Me quiero ir», compartir la ubicación). Activar «¿Todo bien?»
 * sigue hasta el final (puedeActivarTodoBien).
 */
export function enElDia(
	p: { empiezaEn: string; duracionMin: number; cancelado?: boolean },
	ahora: Date = new Date()
): boolean {
	if (p.cancelado) return false;
	const t = new Date(p.empiezaEn).getTime();
	const n = ahora.getTime();
	return n >= t - 3 * HORA && n < t + p.duracionMin * 60_000 + 2 * HORA;
}

/**
 * «Llegué» (el check-in) anda desde media hora antes hasta una hora después del final, como la API
 * (planes/checkin.go: checkinAntes y checkinDespues). Antes, el botón dice desde qué hora.
 */
export function puedeLlegar(
	p: { empiezaEn: string; duracionMin: number },
	ahora: Date = new Date()
): boolean {
	const t = new Date(p.empiezaEn).getTime();
	const n = ahora.getTime();
	return n >= t - 30 * 60_000 && n <= t + p.duracionMin * 60_000 + HORA;
}

/** «Check-in desde 20:30» (cuando todavía es temprano para «Llegué»). */
export function llegarDesde(p: { empiezaEn: string }): string {
	return `Check-in desde ${partes(new Date(new Date(p.empiezaEn).getTime() - 30 * 60_000)).hora}`;
}

/** «¿Todo bien?» se activa desde 3 h antes hasta el final del plan (como la API). */
export function puedeActivarTodoBien(
	p: { empiezaEn: string; duracionMin: number },
	ahora: Date = new Date()
): boolean {
	const t = new Date(p.empiezaEn).getTime();
	return ahora.getTime() < t + p.duracionMin * 60_000;
}

/** «en 25 min» · «en 2 h 10» · «Ya arrancó». */
export function cuentaRegresiva(empieza: string, ahora: Date = new Date()): string {
	const min = minutosHasta(empieza, ahora);
	if (min <= 0) return 'Ya arrancó';
	if (min < 60) return `en ${min} min`;
	const h = Math.floor(min / 60);
	const m = min % 60;
	return m ? `en ${h} h ${String(m).padStart(2, '0')}` : `en ${h} h`;
}

/** La víspera: el plan es mañana (y no empezó). */
export function esVispera(p: { empiezaEn: string }, ahora: Date = new Date()): boolean {
	return diasHasta(p.empiezaEn, ahora) === 1;
}

/** La tarjeta «Esta semana» arriba de Ahora: de lunes a miércoles (el ritual es el lunes). */
export function semanaVisible(ahora: Date = new Date()): boolean {
	const s = partes(ahora).semana;
	return s >= 1 && s <= 3;
}

/**
 * Las caritas del 1 al 5 (como Timeleft): privadas, sólo suman al promedio de quien organiza. Se
 * dibujan con el ícono `carita` (del mismo trazo que el resto), no con emojis del sistema.
 */
export const CARITAS = [
	{ valor: 1, texto: 'Mal' },
	{ valor: 2, texto: 'Flojo' },
	{ valor: 3, texto: 'Bien' },
	{ valor: 4, texto: 'Muy bien' },
	{ valor: 5, texto: 'Genial' }
] as const;

/** «4,5 · 10+ opiniones» (el promedio viene a medio punto y la cantidad en escalones). */
export function caritasTexto(c: { promedio: number; cantidad: number } | undefined): string {
	if (!c || c.cantidad < 3) return '';
	const prom = c.promedio.toLocaleString('es-AR', {
		minimumFractionDigits: 1,
		maximumFractionDigits: 1
	});
	return `${prom}\u00a0·\u00a0${c.cantidad}+\u00a0opiniones`;
}

/** «Es tuyo hasta las 21:40» (el lugar guardado de la lista de espera). Vacío si ya venció. */
export function lugarGuardado(venceEn: string | undefined, ahora: Date = new Date()): string {
	if (!venceEn || new Date(venceEn).getTime() <= ahora.getTime()) return '';
	return `Es tuyo hasta las ${partes(venceEn).hora}`;
}

/** Link a «Cómo llegar» en el mapa del teléfono. */
export function comoLlegar(lat: number, lng: number): string {
	return `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`;
}

/** Link para mandarle la ubicación actual a alguien. */
export function linkUbicacion(lat: number, lng: number): string {
	return `https://maps.google.com/?q=${lat.toFixed(5)},${lng.toFixed(5)}`;
}

/** El contacto es un teléfono (se puede llamar o escribir por WhatsApp). */
export function esTelefono(c: string): boolean {
	return !c.includes('@') && c.replace(/\D/g, '').length >= 6;
}

/** A quién le llega el aviso de «¿Todo bien?»: al contacto si es un mail; si es un teléfono, a vos. */
export function avisaA(c: { contacto: string; avisa?: 'contacto' | 'vos' }): 'contacto' | 'vos' {
	return c.avisa ?? (c.contacto.includes('@') ? 'contacto' : 'vos');
}

/**
 * El número para WhatsApp. Con «+», tal cual (otro país). Si no, Argentina: sin el 0 de larga
 * distancia y sin el 15 de celular que va después de la característica (2 a 4 dígitos), y con 549
 * adelante. «0223 15 592-6772», «223 15 5926772» y «2235926772» dan 5492235926772. Si no quedan 10
 * dígitos nacionales, el número como vino.
 */
export function numeroWhatsapp(tel: string): string {
	const d = tel.replace(/\D/g, '');
	if (tel.trim().startsWith('+')) return d;
	let n = d.replace(/^0/, '');
	if (n.startsWith('549') && n.length === 13) return n;
	if (n.startsWith('54') && n.length === 12) return '549' + n.slice(2);
	if (n.length === 12) {
		for (const k of [2, 3, 4]) {
			if (n.slice(k, k + 2) === '15') {
				n = n.slice(0, k) + n.slice(k + 2);
				break;
			}
		}
	}
	if (n.length === 10) return '549' + n;
	return d;
}

/** wa.me con el número y el texto. */
export function whatsapp(tel: string, texto: string): string {
	return `https://wa.me/${numeroWhatsapp(tel)}?text=${encodeURIComponent(texto)}`;
}

/** «Lunes 24» para la tira de días de «Esta semana». */
const DIAS = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];
export function diaCorto(dia: string): { nombre: string; numero: number } {
	const d = new Date(`${dia}T12:00:00-03:00`);
	const p = partes(d);
	return { nombre: DIAS[p.semana], numero: p.dia };
}
