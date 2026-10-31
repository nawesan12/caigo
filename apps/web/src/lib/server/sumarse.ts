import type { RequestEvent } from '@sveltejs/kit';
import { conSesion } from './api.ts';
import type { Plan } from '#lib/tipos.ts';

export type PedidoSumarse = { cantidad: number; mensaje: string; modo: 'entrada' | 'sena' };

/** Lee cantidad, mensaje y modo de un formulario o de la URL (detalle del plan o pantalla de género). */
export function leerPedido(f: FormData | URLSearchParams): PedidoSumarse {
	return {
		cantidad: Math.min(10, Math.max(1, Number(f.get('cantidad')) || 1)),
		mensaje: String(f.get('mensaje') ?? '')
			.trim()
			.slice(0, 200),
		modo: f.get('modo') === 'sena' ? 'sena' : 'entrada'
	};
}

/**
 * Sumarse a un plan: gratis → POST /sumarme; entrada o seña de un lugar → Checkout Pro de Mercado
 * Pago. Devuelve el estado o el link de pago. Tira ErrorApi (falta_selfie, falta_genero, …).
 */
export async function sumarse(
	event: RequestEvent,
	id: string,
	p: PedidoSumarse
): Promise<{ estado?: string; link?: string }> {
	const plan = await conSesion<Plan>(event, 'GET', `/v1/planes/${id}`);
	if (plan.lugar && plan.cobro !== 'gratis') {
		const c = await conSesion<{ link?: string }>(event, 'POST', `/v1/planes/${id}/entradas`, {
			cuerpo: { cantidad: p.cantidad, modo: p.modo }
		});
		return c?.link ? { link: c.link } : { estado: 'aceptada' };
	}
	const r = await conSesion<{ estado: string }>(event, 'POST', `/v1/planes/${id}/sumarme`, {
		cuerpo: { cantidad: p.cantidad, mensaje: p.mensaje }
	});
	return { estado: r.estado };
}

/** /app/genero con lo necesario para reintentar el pedido después de contestar. */
export function aGenero(id: string, p: PedidoSumarse): string {
	const q = new URLSearchParams({
		plan: id,
		cantidad: String(p.cantidad),
		modo: p.modo
	});
	if (p.mensaje) q.set('mensaje', p.mensaje);
	return `/app/genero?${q}`;
}
