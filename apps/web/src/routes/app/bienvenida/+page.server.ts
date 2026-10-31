import { dev } from '$app/env';
import { redirect } from '@sveltejs/kit';
import { api, ipDe } from '#lib/server/api.ts';
import { conUbicacion } from '#lib/server/app.ts';
import type { Plan } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

/** 01a muestra un plan de verdad que esté pasando ahora (si hay); si no, la tarjeta no va. */
export const load: PageServerLoad = async (event) => {
	let plan: Plan | null = null;
	try {
		const r = await api<{ planes: Plan[] }>(
			'GET',
			`/v1/publico/planes/ahora?${conUbicacion(event)}`,
			{ ip: ipDe(event) }
		);
		plan = r?.planes?.find((p) => p.faltan > 0) ?? null;
	} catch {
		/* sin tarjeta */
	}
	return { plan };
};

export const actions: Actions = {
	/** Saltar, Empezar o "Ya tengo cuenta": no se vuelve a mostrar. */
	default: async ({ request, cookies, locals }) => {
		const a = String((await request.formData()).get('a') ?? '');
		cookies.set('caigo_bienvenida', '1', {
			path: '/',
			httpOnly: true,
			secure: !dev,
			sameSite: 'lax',
			maxAge: 365 * 86400
		});
		if (a === 'cuenta' && !locals.token) redirect(303, '/ingresar?volver=%2Fapp');
		redirect(303, '/app');
	}
};
