import { error, fail } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import type { Arrepentimiento, DNIPendiente, Lugar, Reporte, Yo } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

/** Backoffice del equipo de Caigo: lugares pendientes, DNI, reportes y arrepentimientos. */
export const load: PageServerLoad = async (event) => {
	const yo = await conSesion<Yo>(event, 'GET', '/v1/yo');
	if (!yo.equipo) error(404, 'No encontrado');
	event.setHeaders({ 'cache-control': 'no-store', 'x-robots-tag': 'noindex' });
	const [l, d, r, a] = await Promise.all([
		conSesion<{ lugares: Lugar[] }>(event, 'GET', '/v1/equipo/lugares'),
		conSesion<{ pendientes: DNIPendiente[] }>(event, 'GET', '/v1/equipo/dni'),
		conSesion<{ reportes: Reporte[] }>(event, 'GET', '/v1/equipo/reportes'),
		conSesion<{ arrepentimientos: Arrepentimiento[] }>(event, 'GET', '/v1/equipo/arrepentimientos')
	]);
	const t = event.url.searchParams.get('tab');
	return {
		tab: t === 'dni' || t === 'reportes' || t === 'arrepentimientos' ? t : 'lugares',
		lugares: l.lugares,
		dni: d.pendientes,
		reportes: r.reportes,
		arrepentimientos: a.arrepentimientos
	};
};

function id(f: FormData) {
	const v = String(f.get('id') ?? '');
	if (!/^[0-9a-f-]{36}$/i.test(v)) error(400, 'Falta el id.');
	return v;
}

async function hacer(f: () => Promise<unknown>, ok: string) {
	try {
		await f();
		return { ok };
	} catch (e) {
		if (!(e instanceof ErrorApi)) throw e;
		return fail(400, { error: mensajeDe(e) });
	}
}

export const actions: Actions = {
	lugar: async (event) => {
		const f = await event.request.formData();
		const aprobar = f.get('decision') === 'aprobar';
		return hacer(
			() =>
				conSesion(event, 'POST', `/v1/equipo/lugares/${id(f)}`, {
					cuerpo: { aprobar, motivo: String(f.get('motivo') ?? '').trim() }
				}),
			aprobar ? 'Lugar aprobado.' : 'Lugar rechazado.'
		);
	},
	dni: async (event) => {
		const f = await event.request.formData();
		const aprobar = f.get('decision') === 'aprobar';
		return hacer(
			() =>
				conSesion(event, 'POST', `/v1/equipo/dni/${id(f)}`, {
					cuerpo: { aprobar, motivo: String(f.get('motivo') ?? '').trim() }
				}),
			aprobar ? 'DNI aprobado.' : 'DNI rechazado.'
		);
	},
	arrepentimiento: async (event) => {
		const f = await event.request.formData();
		return hacer(
			() =>
				conSesion(event, 'POST', `/v1/equipo/arrepentimientos/${id(f)}`, {
					cuerpo: { nota: String(f.get('nota') ?? '').trim() }
				}),
			'Pedido atendido.'
		);
	},
	reporte: async (event) => {
		const f = await event.request.formData();
		const d = String(f.get('decision') ?? '');
		return hacer(
			() =>
				conSesion(event, 'POST', `/v1/equipo/reportes/${id(f)}`, {
					cuerpo: {
						descartar: d === 'descartar',
						suspender: d === 'suspender',
						resolucion: String(f.get('resolucion') ?? '').trim()
					}
				}),
			d === 'descartar'
				? 'Reporte descartado.'
				: d === 'suspender'
					? 'Cuenta suspendida.'
					: 'Reporte resuelto.'
		);
	}
};
