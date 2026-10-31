import { error } from '@sveltejs/kit';
import { api, baseApi, ErrorApi, ipDe } from '#lib/server/api.ts';
import { versionOgPlan } from '#lib/og.ts';
import { png } from '#lib/server/og/motor.ts';
import { ogPlan } from '#lib/server/og/plan.ts';
import { fotoComoDato } from '#lib/server/og/fotos.ts';
import { cacheControl, memo, noCambio, respuestaPng } from '#lib/server/og/respuesta.ts';
import type { Plan } from '#lib/tipos.ts';
import type { RequestHandler } from './$types';

/** La imagen para compartir un plan (1200×630): la usan og:image, twitter:image y el JSON-LD. */
export const GET: RequestHandler = async (event) => {
	const { params, url, request } = event;
	if (!/^[a-z0-9-]{1,80}$/.test(params.slug)) error(404, 'No encontrado');
	let plan: Plan;
	try {
		plan = await api<Plan>('GET', `/v1/publico/planes/${encodeURIComponent(params.slug)}`, {
			ip: ipDe(event)
		});
	} catch (e) {
		if (e instanceof ErrorApi && e.estado === 404) error(404, 'No encontrado');
		throw e;
	}
	const version = versionOgPlan(plan);
	const etag = `"p-${version}"`;
	const cc = cacheControl(url.searchParams.get('v'), version);
	const ya = noCambio(request, etag, cc);
	if (ya) return ya;

	const bytes = await memo(`p:${plan.slug}:${version}`, async () => {
		const o = { base: url.origin, api: baseApi() };
		const anf = plan.lugar
			? { nombre: plan.lugar.nombre, foto: plan.lugar.foto, esLugar: true }
			: { nombre: plan.organizador.nombre, foto: plan.organizador.foto, esLugar: false };
		const van = (plan.van ?? []).slice(0, 4);
		const [foto, fotoAnf, ...caras] = await Promise.all([
			fotoComoDato(plan.foto || plan.lugar?.foto, o),
			fotoComoDato(anf.foto, o),
			...van.map((v) => fotoComoDato(v.foto, o))
		]);
		return png(
			ogPlan({
				plan,
				foto,
				anfitrion: { ...anf, foto: fotoAnf },
				caras: van.map((v, i) => ({ nombre: v.nombre, foto: caras[i] }))
			})
		);
	});
	return respuestaPng(bytes, etag, cc);
};
