/**
 * Plata del panel: la lógica es la del script de `Caigo Panel Lugares.dc.html`.
 * - Cargo por servicio: 7,5 % del precio, redondeado a $100. Lo paga quien compra, nunca el lugar.
 * - Seña: el % del precio, redondeado a $100.
 * Todo en pesos enteros; la API trabaja en centavos.
 */

export function cargo(precio: number): number {
	return Math.round((precio * 0.075) / 100) * 100;
}

export function sena(precio: number, pct: number): number {
	return Math.round((precio * pct) / 100 / 100) * 100;
}

export type Cobro = 'gratis' | 'entrada' | 'sena';

/** El desglose "LA PERSONA PAGA / VOS RECIBÍS" del paso Entrada. */
export function desglose(cobro: Cobro, precio: number, pct: number) {
	const esSena = cobro === 'sena';
	const s = sena(precio, pct);
	const lugar = esSena ? s : precio;
	return {
		cargo: cargo(precio),
		sena: s,
		resto: precio - s,
		/** Lo que paga la persona al reservar (entrada o seña, más el cargo). */
		paga: lugar + cargo(precio),
		/** Lo que recibe el lugar por persona al reservar. */
		recibe: lugar
	};
}

const fmt = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 0 });

/** $8.000 */
export function pesos(n: number): string {
	return '$' + fmt.format(Math.round(n));
}

/** De centavos a $8.000. */
export function pesosDeCentavos(c: number | null | undefined): string {
	return pesos((c ?? 0) / 100);
}

/** "8.000" → 8000 (lo que se escribe en el campo de precio). */
export function leerPrecio(texto: string): number {
	return parseInt(String(texto).replace(/\D/g, '').slice(0, 7), 10) || 0;
}
