import { conSesion } from '#lib/server/api.ts';
import type { Plan } from '#lib/tipos.ts';
import type { PageServerLoad } from './$types';

const CUALES = ['organizo', 'me_sume', 'historial'] as const;

/** Mis planes (10): Organizo · Me sumé · Historial. */
export const load: PageServerLoad = async (event) => {
	const c = event.url.searchParams.get('cual') ?? '';
	const cual = (CUALES as readonly string[]).includes(c)
		? (c as (typeof CUALES)[number])
		: 'organizo';
	const traer = (k: string) =>
		conSesion<{ planes: Plan[] }>(event, 'GET', `/v1/planes/mios?cual=${k}`).then((r) => r.planes);
	const [organizo, meSume, historial] = await Promise.all([
		traer('organizo'),
		traer('me_sume'),
		cual === 'historial' ? traer('historial') : Promise.resolve([] as Plan[])
	]);
	return {
		cual,
		cuentas: { organizo: organizo.length, me_sume: meSume.length },
		planes: cual === 'organizo' ? organizo : cual === 'me_sume' ? meSume : historial
	};
};
