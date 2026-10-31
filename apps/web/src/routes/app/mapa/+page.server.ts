import { quizasConSesion } from '#lib/server/api.ts';
import { conUbicacion, ubicacion } from '#lib/server/app.ts';
import { FILTROS_AHORA } from '#lib/app.ts';
import type { Plan } from '#lib/tipos.ts';
import type { PageServerLoad } from './$types';

/** Ahora · mapa (03): los mismos filtros que la lista y el buscador ("q", contrato §5). */
export const load: PageServerLoad = async (event) => {
	const sp = event.url.searchParams;
	const c = sp.get('categoria') ?? '';
	const categoria = FILTROS_AHORA.some((f) => f.valor === c) ? c : '';
	const busqueda = (sp.get('q') ?? '').trim().slice(0, 60);
	const q = busqueda.length >= 2 ? busqueda : '';
	const qs = conUbicacion(event, { categoria, radio: '30', q });
	let { planes } = await quizasConSesion<{ planes: Plan[] }>(
		event,
		`/v1/planes/ahora?${qs}`,
		`/v1/publico/planes/ahora?${qs}`
	);
	// Respaldo mientras la API no filtra por `q`: mismo criterio (título, zona, lugar).
	if (q) {
		const n = (t: string) => t.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
		planes = planes.filter((p) =>
			n([p.titulo, p.zona, p.lugarNombre, p.lugar?.nombre ?? ''].join(' ')).includes(n(q))
		);
	}
	return { planes, categoria, q: busqueda, centro: ubicacion(event) };
};
