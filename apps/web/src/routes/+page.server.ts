import { fail } from '@sveltejs/kit';
import { api, ErrorApi, ipDe, mensajeDe } from '#lib/server/api.ts';
import { CENTRO_MDP, distancia, TINTES } from '#lib/app.ts';
import { cuandoCorto } from '#lib/fechas.ts';
import { CATEGORIAS, primerNombre } from '#lib/plan.ts';
import type { Plan } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

/** Las ciudades de la lista de espera (las dos de Caigo y "otra"). */
const CIUDADES = ['Mar del Plata', 'Villa Gesell', 'Otra ciudad'];

/** Sin foto del plan, la de ejemplo de su categoría (las mismas fotos CC0 de la landing). */
const FOTOS: Record<string, string> = {
	deporte: '/fotos/playa.jpg',
	juego: '/fotos/truco.jpg',
	previa: '/fotos/previa.jpg',
	salida: '/fotos/previa.jpg',
	fiesta: '/fotos/previa.jpg',
	cita_doble: '/fotos/amigos.jpg',
	gastronomia: '/fotos/lugar.jpg',
	musica: '/fotos/lugar.jpg'
};

/**
 * "¿Quién se prende?" con los planes que están pasando (GET /v1/publico/planes/ahora, cerca del
 * centro de Mar del Plata). Si no hay ninguno o la API no contesta, la página usa los de ejemplo.
 */
export const load: PageServerLoad = async (event) => {
	// La misma página para todos: se cachea un minuto (los planes cambian rápido).
	event.setHeaders({ 'cache-control': 'public, max-age=60' });
	let planes: Plan[];
	try {
		const [lat, lng] = CENTRO_MDP;
		planes = (
			await api<{ planes: Plan[] }>('GET', `/v1/publico/planes/ahora?lat=${lat}&lng=${lng}`, {
				ip: ipDe(event)
			})
		).planes;
	} catch {
		planes = [];
	}
	const ahora = planes
		.filter((p) => !p.cancelado && p.faltan > 0)
		.slice(0, 3)
		.map((p) => ({
			id: p.id,
			foto: p.foto || FOTOS[p.categoria] || '/fotos/grupo.jpg',
			tinte: TINTES[p.categoria] ?? '#5B43C9',
			categoria: CATEGORIAS[p.categoria] ?? p.categoria,
			cuando: cuandoCorto(p.empiezaEn),
			titulo: p.titulo,
			donde: [p.lugar?.nombre || p.lugarNombre || p.zona, distancia(p.distanciaM)]
				.filter(Boolean)
				.join(' · '),
			faltan: p.faltan,
			somos: p.somos,
			total: p.total,
			personas: p.van.map((v) => ({ nombre: primerNombre(v.nombre), foto: v.foto }))
		}));
	return { ahora, ciudades: CIUDADES };
};

export const actions: Actions = {
	/** Lista de espera (contrato §8): mail y ciudad. */
	espera: async (event) => {
		const f = await event.request.formData();
		const email = String(f.get('email') ?? '')
			.trim()
			.slice(0, 200);
		const ciudad = String(f.get('ciudad') ?? '');
		if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))
			return fail(400, { espera: { error: 'Revisá el mail.', email, ciudad } });
		if (!CIUDADES.includes(ciudad))
			return fail(400, { espera: { error: 'Elegí tu ciudad.', email, ciudad } });
		try {
			await api('POST', '/v1/publico/espera', { cuerpo: { email, ciudad }, ip: ipDe(event) });
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(e.estado === 429 ? 429 : 400, {
				espera: { error: mensajeDe(e), email, ciudad }
			});
		}
		return { espera: { listo: true, email, ciudad } };
	}
};
