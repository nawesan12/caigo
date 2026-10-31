import { error, fail, redirect } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { lugarAprobado, misLugares, uuid } from '#lib/server/panel.ts';
import type { AsistenteConId, PlanDelLugar, Validacion } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async (event) => {
	const lugar = await lugarAprobado(event);
	const id = uuid(event.params.id);
	const base = `/v1/lugares/${lugar.id}/planes`;
	const noEs = (e: unknown): never => {
		if (e instanceof ErrorApi && e.estado === 404) error(404, 'Ese plan no es de este lugar.');
		throw e;
	};
	const [plan, asist] = await Promise.all([
		conSesion<PlanDelLugar>(event, 'GET', `${base}/${id}`).catch(noEs),
		conSesion<{ asistentes: AsistenteConId[] }>(event, 'GET', `${base}/${id}/asistentes`).catch(
			noEs
		)
	]);
	const q = event.url.searchParams;
	return {
		plan,
		asistentes: asist.asistentes,
		aviso:
			q.get('editado') === '1'
				? 'Cambios guardados.'
				: q.get('empezo') === '1'
					? 'El plan ya empezó: no se puede editar.'
					: ''
	};
};

export const actions: Actions = {
	validar: async (event) => {
		// El código de una entrada (TK-4821, o sólo los números) o el QR de check-in de la app, que es un
		// token firmado: ése va tal cual (no se pasa a mayúsculas).
		let codigo = String((await event.request.formData()).get('codigo') ?? '').trim();
		if (/^(TK-)?\d{4,}$/i.test(codigo)) codigo = codigo.toUpperCase();
		if (!codigo) return fail(400, { error: 'Escribí el código de la entrada.' });
		const { lugar } = await misLugares(event);
		if (!lugar) return fail(400, { error: 'Elegí un lugar.' });
		try {
			const v = await conSesion<Validacion>(event, 'POST', `/v1/lugares/${lugar.id}/validar`, {
				cuerpo: { codigo }
			});
			return { validacion: v };
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
	},
	checkin: async (event) => {
		const id = uuid(event.params.id);
		const persona = uuid(String((await event.request.formData()).get('usuario') ?? ''));
		const { lugar } = await misLugares(event);
		if (!lugar) return fail(400, { error: 'Elegí un lugar.' });
		try {
			await conSesion(event, 'POST', `/v1/lugares/${lugar.id}/planes/${id}/checkin/${persona}`);
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
		return { checkin: true };
	},
	cancelar: async (event) => {
		const id = uuid(event.params.id);
		const { lugar } = await misLugares(event);
		if (!lugar) return fail(400, { error: 'Elegí un lugar.' });
		try {
			await conSesion(event, 'DELETE', `/v1/lugares/${lugar.id}/planes/${id}`);
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
		redirect(303, '/?cancelado=1');
	}
};
