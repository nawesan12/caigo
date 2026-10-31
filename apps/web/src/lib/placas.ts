import type { PedidoPlacas } from './tipos.ts';

/** Cuántas placas pide un lugar de una vez (lo mismo que acepta la API). */
export const CANTIDADES = [1, 2, 5, 10] as const;

/** «1 placa común» · «4 placas NTAG 424». */
export function placasTexto(p: Pick<PedidoPlacas, 'cantidad' | 'tipo'>): string {
	const n = p.cantidad === 1 ? '1 placa' : `${p.cantidad} placas`;
	return p.tipo === 'ntag424' ? `${n} NTAG 424` : `${n} ${p.cantidad === 1 ? 'común' : 'comunes'}`;
}

/** Lo que llega del formulario, ya validado (o el error para mostrar). */
export function leerPedido(
	f: FormData
): { cantidad: number; tipo: 'comun' | 'ntag424'; nota: string } | { error: string } {
	const cantidad = Number(f.get('cantidad'));
	if (!Number.isInteger(cantidad) || cantidad < 1 || cantidad > 20)
		return { error: 'Pedí de 1 a 20 placas.' };
	const tipo = f.get('tipo') === 'ntag424' ? 'ntag424' : 'comun';
	const nota = String(f.get('nota') ?? '')
		.trim()
		.slice(0, 500);
	return { cantidad, tipo, nota };
}
