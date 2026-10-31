import { fail } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { lugarAprobado, misLugares, uuid } from '#lib/server/panel.ts';
import type { Lugar, Placa } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async (event) => {
	const lugar = await lugarAprobado(event);
	const { placas } = await conSesion<{ placas: Placa[] }>(event, 'GET', `/v1/lugares/${lugar.id}/placas`);
	return { placas: placas.filter((p) => p.activa) };
};

async function conLugar<T>(event: Parameters<Actions[string]>[0], f: (l: Lugar) => Promise<T>) {
	const { lugar } = await misLugares(event);
	if (!lugar) return fail(400, { error: 'Elegí un lugar.' });
	try {
		return await f(lugar);
	} catch (e) {
		if (!(e instanceof ErrorApi)) throw e;
		return fail(400, { error: mensajeDe(e) });
	}
}

export const actions: Actions = {
	activar: async (event) => {
		const f = await event.request.formData();
		return conLugar(event, async (l) => {
			const placa = await conSesion<Placa>(event, 'POST', `/v1/lugares/${l.id}/placas`, {
				cuerpo: {
					nombre: String(f.get('nombre') ?? ''),
					uso: f.get('uso') === 'barra' ? 'barra' : 'puerta',
					sun: f.get('sun') === 'on'
				}
			});
			// La clave del chip se muestra esta única vez.
			return { activada: placa };
		});
	},
	baja: async (event) => {
		const id = uuid(String((await event.request.formData()).get('placa') ?? ''));
		return conLugar(event, async (l) => {
			await conSesion(event, 'DELETE', `/v1/lugares/${l.id}/placas/${id}`);
			return { baja: true };
		});
	},
	socio: async (event) => {
		const f = await event.request.formData();
		return conLugar(event, async (l) => {
			// PUT /v1/lugares/{id} lleva los datos del lugar completos más el pase de socio.
			await conSesion(event, 'PUT', `/v1/lugares/${l.id}`, {
				cuerpo: {
					nombre: l.nombre,
					rubro: l.rubro,
					ciudad: l.ciudad,
					zona: l.zona,
					direccion: l.direccion,
					lat: l.lat,
					lng: l.lng,
					foto: l.foto,
					instagram: l.instagram,
					telefono: l.telefono,
					socioActivo: f.get('activo') === 'on',
					socioVisitas: Number(f.get('visitas')) || 5,
					socioBeneficio: String(f.get('beneficio') ?? '').trim()
				}
			});
			return { socioGuardado: true };
		});
	}
};
