import type { Mensaje, Privado } from './tipos.ts';

/**
 * Los eventos en vivo (/app/vivo, que reenvía el WebSocket de la API). Una sola conexión para toda la
 * pestaña: se abre con el primer oyente y se cierra con el último.
 */
export type EventoVivo = {
	/** `moderado`: cambió el estado de moderación de un mensaje (trae el mensaje entero). */
	tipo: 'mensaje' | 'privado' | 'escribiendo' | 'plan' | 'moderado';
	planId?: string;
	mensaje?: Mensaje;
	privado?: Privado;
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
