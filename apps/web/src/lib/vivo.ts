/**
 * Los eventos en vivo (/app/vivo, que reenvía el WebSocket de la API). Una sola conexión para toda la
 * pestaña: se abre con el primer oyente y se cierra con el último.
 */
export type EventoVivo = {
	tipo: 'mensaje' | 'privado' | 'escribiendo' | 'plan';
	planId?: string;
	mensaje?: { planId: string };
	privado?: unknown;
};

const oyentes = new Set<(e: EventoVivo) => void>();
let fuente: EventSource | undefined;

/** Escucha los eventos; devuelve la función para dejar de escuchar (sirve como retorno de onMount). */
export function escucharVivo(f: (e: EventoVivo) => void): () => void {
	oyentes.add(f);
	if (!fuente && typeof EventSource !== 'undefined') {
		fuente = new EventSource('/app/vivo');
		fuente.onmessage = (m) => {
			let e: EventoVivo;
			try {
				e = JSON.parse(m.data);
			} catch {
				return;
			}
			for (const o of oyentes) o(e);
		};
	}
	return () => {
		oyentes.delete(f);
		if (!oyentes.size) {
			fuente?.close();
			fuente = undefined;
		}
	};
}
