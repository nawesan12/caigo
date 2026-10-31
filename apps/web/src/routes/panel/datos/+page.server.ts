import { fail } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { lugarAprobado, misLugares } from '#lib/server/panel.ts';
import type { Lugar } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async (event) => {
	await lugarAprobado(event);
	return {};
};

export const actions: Actions = {
	/** PUT /v1/lugares/{id}: los datos completos (con la foto, §6) y el pase de socio como está. */
	default: async (event) => {
		const { lugar } = await misLugares(event);
		if (!lugar) return fail(400, { error: 'Elegí un lugar.' });
		const f = await event.request.formData();
		const t = (k: string) => String(f.get(k) ?? '').trim();
		const datos = {
			nombre: t('nombre'),
			rubro: t('rubro'),
			ciudad: t('ciudad'),
			zona: t('zona'),
			direccion: t('direccion'),
			lat: Number(t('lat')) || lugar.lat,
			lng: Number(t('lng')) || lugar.lng,
			foto: t('foto'),
			instagram: t('instagram'),
			telefono: t('telefono')
		};
		try {
			await conSesion<Lugar>(event, 'PUT', `/v1/lugares/${lugar.id}`, {
				cuerpo: {
					...datos,
					socioActivo: lugar.socioActivo,
					socioVisitas: lugar.socioVisitas,
					socioBeneficio: lugar.socioBeneficio
				}
			});
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
		return { guardado: true };
	}
};
