import { redirect } from '@sveltejs/kit';
import { conversaciones } from '#lib/server/privados.ts';
import type { PageServerLoad } from './$types';

const UUID = /^[0-9a-f-]{36}$/i;

/**
 * Mensajes (los privados): una tarjeta por persona, la conversación más reciente primero. Los
 * avisos de `privado.nuevo` traen `{"privado": id}`: `/app/mensajes?privado=<id>` abre ese chat.
 */
export const load: PageServerLoad = async (event) => {
	const privado = event.url.searchParams.get('privado') ?? '';
	if (UUID.test(privado)) redirect(303, `/app/mensajes/${privado}`);
	event.depends('app:privados');
	return {
		conversaciones: await conversaciones(event),
		bloqueaste: event.url.searchParams.get('bloqueaste')
	};
};
