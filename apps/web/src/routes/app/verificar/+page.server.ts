import { fail, redirect } from '@sveltejs/kit';
import { volverSeguro } from '#lib/hosts.ts';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { telefonoAR } from '#lib/app.ts';
import { COOKIE_SELFIE } from '#lib/server/cuenta.ts';
import { edad, fechaValida, pasoDe } from '#lib/verificacion.ts';
import type { Actions, PageServerLoad } from './$types';

/**
 * Verificación, una sola vez: 01d teléfono, 01e selfie con prueba de vida y el DNI (frente, dorso,
 * número y fecha de nacimiento; se aprueba al subirlo y el equipo lo revisa después). Sin las tres,
 * /app no deja hacer nada (hooks.server.ts). Después vuelve adonde estaba.
 */
export const load: PageServerLoad = async (event) => {
	const { verif } = await event.parent();
	const volver = volverSeguro(event.url.searchParams.get('volver'), '/app');
	const paso = pasoDe(verif);
	if (paso === 0) redirect(303, volver);
	return {
		paso,
		volver,
		dni: verif.dni,
		motivo: verif.dniMotivo ?? '',
		// Para el DNI hace falta la selfie de la prueba de vida; si no quedó (otro navegador, pasó un
		// día), se saca de nuevo.
		conSelfie: !!event.cookies.get(COOKIE_SELFIE)
	};
};

/**
 * Adónde volver al terminar. Las forms mandan `?/accion`, que reemplaza la query de la página: el
 * `volver` llega en un campo oculto (y, por las dudas, en la URL).
 */
function volverDe(event: { url: URL }, f: FormData): string {
	const v = f.get('volver');
	return volverSeguro(typeof v === 'string' ? v : event.url.searchParams.get('volver'), '/app');
}

export const actions: Actions = {
	telefono: async (event) => {
		const telefono = String((await event.request.formData()).get('telefono') ?? '');
		if (telefono.replace(/\D/g, '').length < 8)
			return fail(400, { error: 'Poné tu número de celular.', telefono });
		try {
			const r = await conSesion<{ via?: string; destino?: string; codigoDePrueba?: string }>(
				event,
				'POST',
				'/v1/verificacion/telefono',
				{
					cuerpo: { telefono: telefonoAR(telefono) }
				}
			);
			// El código va por mail (destino: la casilla enmascarada). Sin correo en la API
			// (desarrollo), viene el código de prueba y se completa solo.
			return {
				enviado: true,
				telefono,
				destino: r?.destino ?? '',
				codigo: r?.codigoDePrueba ?? ''
			};
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
		redirect(303, `/app/verificar?volver=${encodeURIComponent(volverDe(event, f))}`);
	},
	/** El DNI: las fotos ya subidas (/app/verificar/foto), la selfie de la cookie, número y fecha. */
	dni: async (event) => {
		const f = await event.request.formData();
		const numero = String(f.get('numero') ?? '').replace(/\D/g, '');
		const nacimiento = String(f.get('nacimiento') ?? '');
		const frenteId = String(f.get('frenteId') ?? '');
		const dorsoId = String(f.get('dorsoId') ?? '');
		const selfieId = event.cookies.get(COOKIE_SELFIE) ?? '';
		const datos = { numero, nacimiento };
		if (!frenteId || !dorsoId)
			return fail(400, { error: 'Sacale una foto al frente y otra al dorso del DNI.', ...datos });
		if (!/^\d{7,9}$/.test(numero))
			return fail(400, { error: 'Poné tu número de DNI, sin puntos.', ...datos });
		const nac = fechaValida(nacimiento);
		if (!nac || edad(nac) > 110)
			return fail(400, { error: 'Poné tu fecha de nacimiento.', ...datos });
		if (edad(nac) < 18) return fail(400, { error: 'Caigo es solo para mayores de 18.', ...datos });
		if (!selfieId)
			return fail(400, { error: 'Sacate la selfie de nuevo.', selfie: true, ...datos });
		try {
			await conSesion(event, 'POST', '/v1/verificacion/dni', {
				cuerpo: { frenteId, dorsoId, selfieId, numero, nacimiento }
			});
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			// Ya estaba: sigue de largo.
			if (e.codigo !== 'ya_verificado') {
				// La selfie ya no sirve (se purgó o no era de esta cuenta): se saca otra.
				const selfie = e.codigo === 'falta_selfie' || /selfie/i.test(e.detalle);
				if (selfie) event.cookies.delete(COOKIE_SELFIE, { path: '/app/verificar' });
				return fail(400, { error: mensajeDe(e), selfie, ...datos });
			}
		}
		event.cookies.delete(COOKIE_SELFIE, { path: '/app/verificar' });
		redirect(303, volverDe(event, f));
	}
};
