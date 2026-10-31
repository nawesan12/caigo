import { error, fail, redirect } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { lugarAprobado, misLugares, uuid } from '#lib/server/panel.ts';
import type { EdicionPlanLugar, Plan, PlanDelLugar } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

/**
 * Editar un plan del lugar con el mismo wizard de "Nuevo plan". El plan del panel no trae la
 * descripción, lo que incluye, la política ni quién puede sumarse: salen del plan completo
 * (/v1/planes/{id}).
 */
export const load: PageServerLoad = async (event) => {
	const lugar = await lugarAprobado(event);
	const id = uuid(event.params.id);
	const noEs = (e: unknown): never => {
		if (e instanceof ErrorApi && e.estado === 404) error(404, 'Ese plan no es de este lugar.');
		throw e;
	};
	const [pl, plan] = await Promise.all([
		conSesion<PlanDelLugar>(event, 'GET', `/v1/lugares/${lugar.id}/planes/${id}`).catch(noEs),
		conSesion<Plan>(event, 'GET', `/v1/planes/${id}`).catch(noEs)
	]);
	if (pl.cancelado) redirect(303, `/plan/${id}`);
	if (new Date(pl.empiezaEn).getTime() <= Date.now()) redirect(303, `/plan/${id}?empezo=1`);
	const edicion: EdicionPlanLugar = {
		id,
		titulo: pl.titulo,
		categoria: pl.categoria,
		descripcion: plan.descripcion ?? '',
		foto: pl.foto,
		empiezaEn: pl.empiezaEn,
		duracionMin: pl.duracionMin,
		cupo: pl.cupo,
		confirmados: pl.confirmados,
		entradas: pl.entradas,
		aceptaGrupos: plan.aceptaGrupos,
		recurrente: !!plan.recurrente,
		cobro: pl.cobro,
		precioCentavos: pl.precioCentavos,
		senaPct: pl.senaPct,
		incluye: plan.incluye ?? '',
		cancelacion: plan.cancelacion ?? 'flex',
		beneficio: pl.beneficio ?? '',
		quien:
			plan.genero === 'mujeres' ? 'mujeres' : plan.soloVerificados ? 'verificados' : 'cualquiera'
	};
	return { mpConectado: lugar.mpConectado, edicion };
};

export const actions: Actions = {
	default: async (event) => {
		const id = uuid(event.params.id);
		const { lugar } = await misLugares(event);
		if (!lugar) return fail(400, { error: 'Elegí un lugar.' });
		let datos: Record<string, unknown>;
		try {
			datos = JSON.parse(String((await event.request.formData()).get('datos') ?? '{}'));
		} catch {
			return fail(400, { error: 'Revisá el plan.' });
		}
		try {
			await conSesion(event, 'PATCH', `/v1/lugares/${lugar.id}/planes/${id}`, { cuerpo: datos });
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			if (e.codigo === 'con_entradas')
				return fail(409, {
					error: 'Ya hay entradas vendidas: el precio y el cobro no se pueden cambiar.'
				});
			return fail(400, { error: mensajeDe(e) });
		}
		redirect(303, `/plan/${id}?editado=1`);
	}
};
