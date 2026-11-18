import { error, fail } from '@sveltejs/kit';
import { conSesion, ErrorApi, FOTO_MAX, FOTO_PESADA, mensajeDe } from '#lib/server/api.ts';
import { uuid } from '#lib/server/panel.ts';
import type { FotoPlan, Plan } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

const TIPOS = ['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif'];

/** Fotos del plan: las suben y las ven sólo quienes fueron (informe del 18/11, B4). */
export const load: PageServerLoad = async (event) => {
	const id = uuid(event.params.id);
	try {
		const plan = await conSesion<Plan>(event, 'GET', `/v1/planes/${id}`);
		let fotos: FotoPlan[] = [];
		let cerrado = '';
		// Un error que no es «no fuiste» no es «no hay fotos»: la pantalla ofrece probar de nuevo.
		let falla = '';
		try {
			fotos = (await conSesion<{ fotos: FotoPlan[] }>(event, 'GET', `/v1/planes/${id}/fotos`))
				.fotos;
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			if (e.estado === 404) cerrado = mensajeDe(e);
			else falla = mensajeDe(e);
		}
		return { plan, fotos, cerrado, error: falla };
	} catch (e) {
		if (e instanceof ErrorApi && e.estado === 404) error(404, e.detalle);
		throw e;
	}
};

async function sobreFoto(
	event: Parameters<Actions[string]>[0],
	metodo: 'DELETE' | 'POST',
	sufijo = ''
) {
	const id = uuid(event.params.id);
	const foto = uuid(String((await event.request.formData()).get('foto') ?? ''));
	try {
		await conSesion(event, metodo, `/v1/planes/${id}/fotos/${foto}${sufijo}`);
	} catch (e) {
		if (!(e instanceof ErrorApi)) throw e;
		return fail(400, { error: mensajeDe(e) });
	}
	return { listo: true };
}

export const actions: Actions = {
	subir: async (event) => {
		const id = uuid(event.params.id);
		const archivo = (await event.request.formData()).get('foto');
		if (!(archivo instanceof File) || !archivo.size) return fail(400, { error: 'Elegí una foto.' });
		if (!TIPOS.includes(archivo.type))
			return fail(400, { error: 'Subí una foto (JPG, PNG o WebP).' });
		if (archivo.size > FOTO_MAX) return fail(413, { error: FOTO_PESADA });
		try {
			await conSesion(event, 'POST', `/v1/planes/${id}/fotos`, {
				crudo: await archivo.arrayBuffer(),
				tipo: archivo.type
			});
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(e.estado === 409 ? 409 : 400, { error: mensajeDe(e) });
		}
		return { subida: true };
	},
	borrar: async (event) => sobreFoto(event, 'DELETE'),
	reportar: async (event) => {
		const f = await event.request.clone().formData();
		const motivo = f.get('motivo') === 'salgo_yo' ? 'salgo_yo' : 'inapropiada';
		return sobreFoto(event, 'POST', `/reportar?motivo=${motivo}`);
	}
};
