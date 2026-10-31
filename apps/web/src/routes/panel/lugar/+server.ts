import { redirect } from '@sveltejs/kit';
import { elegirLugar } from '#lib/server/panel.ts';
import type { RequestHandler } from './$types';

/** El selector de lugares del panel. */
export const POST: RequestHandler = async (event) => {
	const id = String((await event.request.formData()).get('id') ?? '');
	if (id === 'nuevo') redirect(303, '/registro');
	if (/^[0-9a-f-]{36}$/i.test(id)) elegirLugar(event, id);
	redirect(303, '/');
};
