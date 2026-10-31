import { conSesion } from '#lib/server/api.ts';
import { diasHasta, esHoy } from '#lib/fechas.ts';
import type { Asistente, Lugar, PlanDelLugar } from '#lib/tipos.ts';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async (event) => {
	const { lugar } = (await event.parent()) as { lugar: Lugar | null };
	if (!lugar || lugar.estado !== 'aprobado') return { aprobado: false as const };
	const cual = event.url.searchParams.get('cual') === 'pasados' ? 'pasados' : 'proximos';
	const base = `/v1/lugares/${lugar.id}/planes`;
	const [prox, pas] = await Promise.all([
		conSesion<{ planes: PlanDelLugar[] }>(event, 'GET', `${base}?cual=proximos`),
		cual === 'pasados'
			? conSesion<{ planes: PlanDelLugar[] }>(event, 'GET', `${base}?cual=pasados`)
			: Promise.resolve(null)
	]);
	const ahora = new Date();
	const semana = prox.planes.filter((p) => !p.cancelado && diasHasta(p.empiezaEn, ahora) < 7);
	// Check-ins de hoy: los asistentes con check-in de los planes de hoy.
	const deHoy = prox.planes.filter((p) => esHoy(p.empiezaEn, ahora));
	const asist = await Promise.all(
		deHoy.map((p) =>
			conSesion<{ asistentes: Asistente[] }>(event, 'GET', `${base}/${p.id}/asistentes`)
				.then((r) => r.asistentes)
				.catch(() => [] as Asistente[])
		)
	);
	const checkins = asist.flat().reduce((n, a) => n + (a.checkin ? a.cantidad : 0), 0);
	return {
		aprobado: true as const,
		cual,
		planes: (pas ?? prox).planes,
		kpis: {
			publicados: semana.length,
			confirmados: prox.planes.reduce((n, p) => n + (p.cancelado ? 0 : p.confirmados), 0),
			checkins
		},
		nuevo: event.url.searchParams.get('nuevo') ?? '',
		cancelado: event.url.searchParams.get('cancelado') === '1'
	};
};
