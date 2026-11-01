import { fail } from '@sveltejs/kit';
import { conSesion, ErrorApi, mensajeDe } from '#lib/server/api.ts';
import { uuid } from '#lib/server/panel.ts';
import { sinLeer } from '#lib/server/privados.ts';
import type { Plan } from '#lib/tipos.ts';
import type { Actions, PageServerLoad } from './$types';

const CUALES = ['organizo', 'me_sume', 'historial'] as const;

/** Mis planes (10): Organizo · Me sumé · Historial, y el acceso a Mensajes con sus no leídos. */
export const load: PageServerLoad = async (event) => {
	const c = event.url.searchParams.get('cual') ?? '';
	const cual = (CUALES as readonly string[]).includes(c)
		? (c as (typeof CUALES)[number])
		: 'organizo';
	const traer = (k: string) =>
		conSesion<{ planes: Plan[] }>(event, 'GET', `/v1/planes/mios?cual=${k}`).then((r) => r.planes);
	const [organizo, meSume, historial, mensajes] = await Promise.all([
		traer('organizo'),
		traer('me_sume'),
		cual === 'historial' ? traer('historial') : Promise.resolve([] as Plan[]),
		sinLeer(event)
	]);
	return {
		cual,
		cuentas: { organizo: organizo.length, me_sume: meSume.length },
		/** Privados sin leer: el contador del acceso a Mensajes. */
		mensajes,
		planes: cual === 'organizo' ? organizo : cual === 'me_sume' ? meSume : historial,
		cancelado: event.url.searchParams.get('cancelado') === '1',
		salio: event.url.searchParams.get('salio') === '1'
	};
};

export const actions: Actions = {
	/** Cancelar un plan que organizo: la API avisa a los que van y devuelve lo pagado. */
	cancelar: async (event) => {
		const id = uuid(String((await event.request.formData()).get('id') ?? ''));
		try {
			await conSesion(event, 'DELETE', `/v1/planes/${id}`);
		} catch (e) {
			if (!(e instanceof ErrorApi)) throw e;
			return fail(400, { error: mensajeDe(e) });
		}
		return { cancelado: id };
	}
};
