import { error } from '@sveltejs/kit';
import { conSesion } from '#lib/server/api.ts';
import { lugarAprobado, uuid } from '#lib/server/panel.ts';
import type { Placa } from '#lib/tipos.ts';
import { qrSvg } from '../qr.ts';
import type { PageServerLoad } from './$types';

/** La placa para imprimir: el QR (hecho acá, sin servicios externos) lleva la URL firmada de la placa. */
export const load: PageServerLoad = async (event) => {
	const lugar = await lugarAprobado(event);
	const id = uuid(event.params.placa);
	const { placas } = await conSesion<{ placas: Placa[] }>(
		event,
		'GET',
		`/v1/lugares/${lugar.id}/placas`
	);
	const placa = placas.find((p) => p.id === id && p.activa);
	if (!placa) error(404, 'Esa placa no está activa en este lugar.');
	return { placa, qr: qrSvg(placa.url) };
};
