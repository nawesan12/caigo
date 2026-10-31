import { redirect } from '@sveltejs/kit';
import { conSesion } from '#lib/server/api.ts';
import { lugarAprobado } from '#lib/server/panel.ts';
import type { PlanDelLugar } from '#lib/tipos.ts';
import type { PageServerLoad } from './$types';

/** "Check-in de hoy": va al próximo plan (el de hoy, si hay). */
export const load: PageServerLoad = async (event) => {
	const lugar = await lugarAprobado(event);
	const { planes } = await conSesion<{ planes: PlanDelLugar[] }>(
		event,
		'GET',
		`/v1/lugares/${lugar.id}/planes?cual=proximos`
	);
	const p = planes.filter((x) => !x.cancelado).sort((a, b) => a.empiezaEn.localeCompare(b.empiezaEn))[0];
	if (p) redirect(303, `/plan/${p.id}`);
	return {};
};
