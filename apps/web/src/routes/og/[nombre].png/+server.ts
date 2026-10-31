import { error } from '@sveltejs/kit';
import { OG_FIJAS, VERSION_FIJAS, type OgFija } from '#lib/og.ts';
import { png } from '#lib/server/og/motor.ts';
import { FIJAS } from '#lib/server/og/fijas.ts';
import { cacheControl, memo, noCambio, respuestaPng } from '#lib/server/og/respuesta.ts';
import type { RequestHandler } from './$types';

/** /og/inicio.png, /og/plus.png, /og/lugares.png y /og/caigo.png (la genérica). */
export const GET: RequestHandler = async ({ params, url, request }) => {
	const nombre = params.nombre as OgFija;
	if (!Object.hasOwn(OG_FIJAS, nombre)) error(404, 'No encontrado');
	const etag = `"f-${nombre}-${VERSION_FIJAS}"`;
	const cc = cacheControl(url.searchParams.get('v'), VERSION_FIJAS);
	const ya = noCambio(request, etag, cc);
	if (ya) return ya;
	const bytes = await memo(`f:${nombre}:${VERSION_FIJAS}`, () => png(FIJAS[nombre]()));
	return respuestaPng(bytes, etag, cc);
};
