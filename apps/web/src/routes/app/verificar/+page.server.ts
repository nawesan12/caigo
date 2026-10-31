import { fail, redirect } from '@sveltejs/kit';
import { volverSeguro } from '#lib/hosts.ts';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { telefonoAR } from '#lib/app.ts';
import type { Actions, PageServerLoad } from './$types';

/** Verificación (01d teléfono + 01e selfie), una sola vez. Después vuelve adonde estaba. */
export const load: PageServerLoad = async (event) => {
	const { verif } = await event.parent();
	const volver = volverSeguro(event.url.searchParams.get('volver'), '/app');
	if (verif.telefono && verif.selfie) redirect(303, volver);
	return { paso: verif.telefono ? 2 : 1, volver };
};

export const actions: Actions = {
	telefono: async (event) => {
		const telefono = String((await event.request.formData()).get('telefono') ?? '');
		if (telefono.replace(/\D/g, '').length < 8)
			return fail(400, { error: 'Poné tu número de celular.', telefono });
		try {
			const r = await conSesion<{ codigoDePrueba?: string }>(
				event,
				'POST',
				'/v1/verificacion/telefono',
				{
					cuerpo: { telefono: telefonoAR(telefono) }
				}
			);
			// Con el SMS simulado la API devuelve el código: se completa solo.
			return { enviado: true, telefono, codigo: r?.codigoDePrueba ?? '' };
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e), telefono });
		}
	},
	confirmar: async (event) => {
		const f = await event.request.formData();
		const codigo = String(f.get('codigo') ?? '').replace(/\D/g, '');
		const telefono = String(f.get('telefono') ?? '');
		if (codigo.length !== 6)
			return fail(400, { error: 'El código tiene 6 números.', enviado: true, telefono });
		try {
			await conSesion(event, 'POST', '/v1/verificacion/telefono/confirmar', { cuerpo: { codigo } });
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e), enviado: true, telefono });
		}
		redirect(
			303,
			`/app/verificar?volver=${encodeURIComponent(volverSeguro(event.url.searchParams.get('volver'), '/app'))}`
		);
	}
};
