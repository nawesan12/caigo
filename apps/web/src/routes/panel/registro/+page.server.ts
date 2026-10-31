import { fail, redirect } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { elegirLugar } from '#lib/server/panel.ts';
import type { Lugar } from '#lib/tipos.ts';
import type { Actions } from './$types';

export const actions: Actions = {
	default: async (event) => {
		const f = await event.request.formData();
		const t = (k: string) => String(f.get(k) ?? '').trim();
		const datos = {
			nombre: t('nombre'),
			rubro: t('rubro'),
			ciudad: t('ciudad'),
			zona: t('zona'),
			direccion: t('direccion'),
			lat: Number(t('lat')) || 0,
			lng: Number(t('lng')) || 0,
			instagram: t('instagram'),
			telefono: t('telefono')
		};
		let lugar: Lugar;
		try {
			lugar = await conSesion<Lugar>(event, 'POST', '/v1/lugares', { cuerpo: datos });
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e), datos });
		}
		elegirLugar(event, lugar.id);
		redirect(303, '/');
	}
};
