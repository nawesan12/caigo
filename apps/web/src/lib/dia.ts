/**
 * El día del plan y después (informe del 18/11): cuándo se muestra «Hoy», la cuenta regresiva, la
 * víspera, las caritas y el ritual de los lunes. Todo en hora de Argentina.
 */
import { diasHasta, partes } from './fechas.ts';

const HORA = 3600_000;

/** «Hoy» (cuenta regresiva, Llegué, ¿Todo bien?) va desde 3 h antes hasta el final del plan. */
export function enElDia(
	p: { empiezaEn: string; duracionMin: number; cancelado?: boolean },
	ahora: Date = new Date()
): boolean {
	if (p.cancelado) return false;
	const t = new Date(p.empiezaEn).getTime();
	const n = ahora.getTime();
	return n >= t - 3 * HORA && n < t + p.duracionMin * 60_000;
}

/** «en 25 min» · «en 2 h 10» · «Ya arrancó». */
export function cuentaRegresiva(empieza: string, ahora: Date = new Date()): string {
	const min = Math.ceil((new Date(empieza).getTime() - ahora.getTime()) / 60_000);
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

/** Las caritas del 1 al 5 (como Timeleft): privadas, sólo suman al promedio de quien organiza. */
export const CARITAS = [
	{ valor: 1, cara: '😣', texto: 'Mal' },
	{ valor: 2, cara: '😕', texto: 'Flojo' },
	{ valor: 3, cara: '🙂', texto: 'Bien' },
	{ valor: 4, cara: '😄', texto: 'Muy bien' },
	{ valor: 5, cara: '🤩', texto: 'Genial' }
] as const;

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

/** wa.me con el número (Argentina por defecto) y el texto. */
export function whatsapp(tel: string, texto: string): string {
	let n = tel.replace(/\D/g, '');
	if (!tel.trim().startsWith('+')) n = '549' + n.replace(/^0/, '').replace(/^15/, '');
	return `https://wa.me/${n}?text=${encodeURIComponent(texto)}`;
}

/** «Lunes 24» para la tira de días de «Esta semana». */
const DIAS = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];
export function diaCorto(dia: string): { nombre: string; numero: number } {
	const d = new Date(`${dia}T12:00:00-03:00`);
	const p = partes(d);
	return { nombre: DIAS[p.semana], numero: p.dia };
}
