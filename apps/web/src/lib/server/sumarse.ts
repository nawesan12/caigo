import type { RequestEvent } from '@sveltejs/kit';
import { conSesion } from './api.ts';
import type { Plan } from '#lib/tipos.ts';

/**
 * mujer / diversidad: la persona confirma, sólo para este pedido, que es mujer o parte de la
 * diversidad (planes «Solo mujeres» / «Solo diversidades»). No se guarda en ningún lado (AAIP 1.2).
 */
export type PedidoSumarse = {
	cantidad: number;
	mensaje: string;
	modo: 'entrada' | 'sena';
	mujer: boolean;
	diversidad: boolean;
};

/** Lee el pedido del formulario del detalle del plan. */
export function leerPedido(f: FormData | URLSearchParams): PedidoSumarse {
	return {
		mujer: f.get('mujer') === 'si',
		diversidad: f.get('diversidad') === 'si',
		cantidad: Math.min(10, Math.max(1, Number(f.get('cantidad')) || 1)),
		mensaje: String(f.get('mensaje') ?? '')
			.trim()
			.slice(0, 200),
		modo: f.get('modo') === 'sena' ? 'sena' : 'entrada'
	};
}

/**
 * Sumarse a un plan: gratis → POST /sumarme; entrada o seña de un lugar → Checkout Pro de Mercado
 * Pago. Devuelve el estado o el link de pago. Tira ErrorApi (falta_selfie, confirmar_mujer, …).
 */
export async function sumarse(
	event: RequestEvent,
	id: string,
	p: PedidoSumarse
): Promise<{ estado?: string; link?: string }> {
	const plan = await conSesion<Plan>(event, 'GET', `/v1/planes/${id}`);
	if (plan.lugar && plan.cobro !== 'gratis') {
		const c = await conSesion<{ link?: string }>(event, 'POST', `/v1/planes/${id}/entradas`, {
			cuerpo: { cantidad: p.cantidad, modo: p.modo, mujer: p.mujer, diversidad: p.diversidad }
		});
		return c?.link ? { link: c.link } : { estado: 'aceptada' };
	}
	const r = await conSesion<{ estado: string }>(event, 'POST', `/v1/planes/${id}/sumarme`, {
		cuerpo: {
			cantidad: p.cantidad,
			mensaje: p.mensaje,
			mujer: p.mujer,
			diversidad: p.diversidad
		}
	});
	return { estado: r.estado };
}
