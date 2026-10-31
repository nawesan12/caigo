import { error, fail, type RequestEvent } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import type {
	Arrepentimiento,
	DNIPendiente,
	Lugar,
	PedidoPlacas,
	PlanEquipo,
	Reporte,
	SemanaMetricas,
	UsuarioEquipo,
	Yo
} from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

/**
 * Backoffice del equipo de Caigo: métricas, usuarios, planes, lugares (todos y pendientes), DNI,
 * reportes, arrepentimientos, pedidos de placas y planes semilla (contrato §7). Cada sección carga por separado: si
 * una ruta de la API todavía no existe o falla, esa sección muestra el aviso y el resto anda.
 */

const TABS = [
	'metricas',
	'usuarios',
	'planes',
	'locales',
	'lugares',
	'dni',
	'reportes',
	'arrepentimientos',
	'placas',
	'semilla'
] as const;
type Tab = (typeof TABS)[number];

const NO_ESTA = 'Todavía no está en la API.';

/** Lo que trae una sección: los datos o por qué no se pudieron traer. */
type Seccion<T> = { datos: T | null; error: string };

async function intentar<T>(event: RequestEvent, ruta: string): Promise<Seccion<T>> {
	try {
		return { datos: await conSesion<T>(event, 'GET', ruta), error: '' };
	} catch (e) {
		if (!(e instanceof ErrorApi)) throw e;
		return { datos: null, error: e.estado === 404 || e.estado === 405 ? NO_ESTA : mensajeDe(e) };
	}
}

const num = (v: unknown) => (typeof v === 'number' && isFinite(v) ? v : Number(v) || 0);
/** Proporción de 0 a 1 (si la API la manda en %, la pasa a proporción). */
const prop = (v: unknown) => {
	const n = num(v);
	return n > 1 ? n / 100 : n;
};

/** Acepta los nombres del contrato y variantes cercanas, para no depender de cómo quede la API. */
function normalizar(r: unknown): SemanaMetricas[] {
	const o = (r ?? {}) as Record<string, unknown>;
	const lista = (Array.isArray(r) ? r : (o.semanas ?? o.metricas ?? [])) as Record<
		string,
		unknown
	>[];
	if (!Array.isArray(lista)) return [];
	return lista
		.map((s) => ({
			semana: String(s.semana ?? s.desde ?? s.inicio ?? '').slice(0, 10),
			publicados: num(s.publicados ?? s.planesPublicados),
			completados: num(s.completados ?? s.planesCompletados),
			asistencia: prop(s.asistencia),
			horasHastaCompletar: num(
				s.horasHastaCompletar ?? s.horasCompletar ?? s.horasPromedio ?? s.horas
			),
			repeticion30: prop(s.repeticion30d ?? s.repeticion30 ?? s.repeticion),
			organizadores: num(s.organizadoresActivos ?? s.organizadores),
			// `altas` es el total; el desglose viene en `altasPorOrigen`.
			altas: Object.fromEntries(
				Object.entries((s.altasPorOrigen ?? {}) as Record<string, unknown>).map(([k, v]) => [
					k,
					num(v)
				])
			)
		}))
		.sort((a, b) => a.semana.localeCompare(b.semana))
		.slice(-8);
}

export const load: PageServerLoad = async (event) => {
	const yo = await conSesion<Yo>(event, 'GET', '/v1/yo');
	if (!yo.equipo) error(404, 'No encontrado');
	event.setHeaders({ 'cache-control': 'no-store', 'x-robots-tag': 'noindex' });

	const u = event.url.searchParams;
	const t = u.get('tab') as Tab;
	const tab: Tab = TABS.includes(t) ? t : 'metricas';
	const q = (u.get('q') ?? '').trim().slice(0, 80);
	const cual = u.get('cual') === 'reportados' ? 'reportados' : 'proximos';
	const estado = ['pendiente', 'aprobado', 'rechazado', 'suspendido'].includes(
		u.get('estado') ?? ''
	)
		? (u.get('estado') as string)
		: '';
	const qs = (o: Record<string, string>) => {
		const p = new URLSearchParams();
		for (const [k, v] of Object.entries(o)) if (v) p.set(k, v);
		const s = p.toString();
		return s ? `?${s}` : '';
	};

	// Las colas se cargan siempre (sus números van en la barra).
	const [l, d, r, a, pp] = await Promise.all([
		intentar<{ lugares: Lugar[] }>(event, '/v1/equipo/lugares?estado=pendiente'),
		intentar<{ pendientes: DNIPendiente[] }>(event, '/v1/equipo/dni'),
		intentar<{ reportes: Reporte[] }>(event, '/v1/equipo/reportes'),
		intentar<{ arrepentimientos: Arrepentimiento[] }>(event, '/v1/equipo/arrepentimientos'),
		intentar<{ pedidos: PedidoPlacas[] }>(event, '/v1/equipo/pedidos-placas')
	]);

	let usuarios: Seccion<UsuarioEquipo[]> = { datos: null, error: '' };
	let planes: Seccion<PlanEquipo[]> = { datos: null, error: '' };
	let locales: Seccion<Lugar[]> = { datos: null, error: '' };
	let metricas: Seccion<SemanaMetricas[]> = { datos: null, error: '' };
	if (tab === 'usuarios') {
		const s = await intentar<{ usuarios: UsuarioEquipo[] }>(
			event,
			`/v1/equipo/usuarios${qs({ q })}`
		);
		usuarios = { datos: s.datos?.usuarios ?? null, error: s.error };
	} else if (tab === 'planes') {
		const s = await intentar<{ planes: PlanEquipo[] }>(
			event,
			`/v1/equipo/planes${qs({ q, cual })}`
		);
		planes = { datos: s.datos?.planes ?? null, error: s.error };
	} else if (tab === 'locales') {
		const s = await intentar<{ lugares: Lugar[] }>(event, `/v1/equipo/lugares${qs({ estado })}`);
		locales = { datos: s.datos?.lugares ?? null, error: s.error };
	} else if (tab === 'metricas') {
		const s = await intentar<unknown>(event, '/v1/equipo/metricas');
		metricas = { datos: s.datos == null ? null : normalizar(s.datos), error: s.error };
	}

	const org = u.get('org') ?? '';
	return {
		tab,
		q,
		cual,
		estado,
		yo: { id: yo.id, nombre: yo.nombre },
		org: /^[0-9a-f-]{36}$/i.test(org) ? org : yo.id,
		orgNombre: u.get('nombre')?.slice(0, 40) ?? '',
		lugares: l.datos?.lugares ?? [],
		lugaresError: l.error,
		dni: d.datos?.pendientes ?? [],
		dniError: d.error,
		reportes: r.datos?.reportes ?? [],
		reportesError: r.error,
		arrepentimientos: a.datos?.arrepentimientos ?? [],
		arrepentimientosError: a.error,
		placas: pp.datos?.pedidos ?? [],
		placasError: pp.error,
		usuarios,
		planes,
		locales,
		metricas
	};
};

function id(f: FormData) {
	const v = String(f.get('id') ?? '');
	if (!/^[0-9a-f-]{36}$/i.test(v)) error(400, 'Falta el id.');
	return v;
}

const motivo = (f: FormData, campo = 'motivo') =>
	String(f.get(campo) ?? '')
		.trim()
		.slice(0, 300);

async function hacer(f: () => Promise<unknown>, ok: string) {
	try {
		await f();
		return { ok };
	} catch (e) {
		if (!(e instanceof ErrorApi)) throw e;
		return fail(400, {
			error: e.estado === 404 || e.estado === 405 ? NO_ESTA : mensajeDe(e)
		});
	}
}

export const actions: Actions = {
	lugar: async (event) => {
		const f = await event.request.formData();
		const aprobar = f.get('decision') === 'aprobar';
		return hacer(
			() =>
				conSesion(event, 'POST', `/v1/equipo/lugares/${id(f)}`, {
					cuerpo: { aprobar, motivo: motivo(f) }
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
					cuerpo: { aprobar, motivo: motivo(f) }
				}),
			aprobar ? 'DNI aprobado.' : 'DNI rechazado.'
		);
	},
	arrepentimiento: async (event) => {
		const f = await event.request.formData();
		return hacer(
			() =>
				conSesion(event, 'POST', `/v1/equipo/arrepentimientos/${id(f)}`, {
					cuerpo: { nota: motivo(f, 'nota') }
				}),
			'Pedido atendido.'
		);
	},
	placas: async (event) => {
		const f = await event.request.formData();
		return hacer(
			() =>
				conSesion(event, 'POST', `/v1/equipo/pedidos-placas/${id(f)}/atendido`, {
					cuerpo: { nota: motivo(f, 'nota') }
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
						resolucion: motivo(f, 'resolucion')
					}
				}),
			d === 'descartar'
				? 'Reporte descartado.'
				: d === 'suspender'
					? 'Cuenta suspendida.'
					: 'Reporte resuelto.'
		);
	},
	usuario: async (event) => {
		const f = await event.request.formData();
		const suspender = f.get('decision') === 'suspender';
		const m = motivo(f);
		if (suspender && !m) return fail(400, { error: 'Poné el motivo de la suspensión.' });
		return hacer(
			() =>
				conSesion(
					event,
					'POST',
					`/v1/equipo/usuarios/${id(f)}/${suspender ? 'suspender' : 'reactivar'}`,
					{ cuerpo: suspender ? { motivo: m } : {} }
				),
			suspender ? 'Cuenta suspendida.' : 'Cuenta reactivada.'
		);
	},
	plan: async (event) => {
		const f = await event.request.formData();
		const m = motivo(f);
		if (!m) return fail(400, { error: 'Poné el motivo: les llega a quienes iban.' });
		return hacer(
			() =>
				conSesion(event, 'POST', `/v1/equipo/planes/${id(f)}/cancelar`, { cuerpo: { motivo: m } }),
			'Plan cancelado. Avisamos a todos y devolvemos lo que pagaron.'
		);
	},
	local: async (event) => {
		const f = await event.request.formData();
		const suspender = f.get('decision') === 'suspender';
		return hacer(
			() =>
				conSesion(
					event,
					'POST',
					`/v1/equipo/lugares/${id(f)}/${suspender ? 'suspender' : 'reactivar'}`,
					{ cuerpo: { motivo: motivo(f) } }
				),
			suspender ? 'Lugar suspendido.' : 'Lugar reactivado.'
		);
	},
	semilla: async (event) => {
		let datos: Record<string, unknown>;
		try {
			datos = JSON.parse(String((await event.request.formData()).get('datos') ?? '{}'));
		} catch {
			return fail(400, { error: 'Revisá el plan.' });
		}
		if (!/^[0-9a-f-]{36}$/i.test(String(datos.organizadorId ?? '')))
			return fail(400, { error: 'Falta quién organiza (el id de una persona).' });
		try {
			const p = await conSesion<{ id: string; slug: string; titulo: string }>(
				event,
				'POST',
				'/v1/equipo/planes',
				{ cuerpo: datos }
			);
			return { ok: 'Plan semilla publicado.', semilla: { slug: p.slug, titulo: p.titulo } };
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, {
				error: e.estado === 404 || e.estado === 405 ? NO_ESTA : mensajeDe(e)
			});
		}
	}
};
