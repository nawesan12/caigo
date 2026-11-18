/** Respuesta de /app/plan/{id}/gesto. */
export type ResultadoGesto = { estado?: string; ok?: boolean; ir?: string; error?: string };

/** «Caigo», «No me interesa» y «Deshacer» desde la lista (ver routes/app/plan/[id]/gesto). */
export async function gesto(
	planId: string,
	accion: 'caigo' | 'ocultar' | 'mostrar'
): Promise<ResultadoGesto> {
	try {
		const r = await fetch(`/app/plan/${planId}/gesto`, {
			method: 'POST',
			headers: { 'content-type': 'application/json' },
			body: JSON.stringify({ accion })
		});
		const b = (await r.json().catch(() => ({}))) as ResultadoGesto;
		if (!r.ok && !b.error) b.error = 'No pudimos hacerlo. Probá de nuevo.';
		return b;
	} catch {
		return { error: 'Sin conexión. Probá de nuevo.' };
	}
}
