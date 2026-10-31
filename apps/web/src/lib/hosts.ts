/**
 * Un solo proyecto sirve dos hosts: caigo.com.ar (landing, link público, ingreso, equipo) y
 * lugares.caigo.com.ar (el panel de lugares). En local: localhost:5173 y lugares.localhost:5173.
 *
 * Las rutas del panel viven en src/routes/panel; `rutaInterna` las reescribe (hooks.ts → reroute)
 * para que en lugares.caigo.com.ar se vean sin el /panel adelante.
 */

export type Sitio = 'caigo' | 'lugares';

/** Qué sitio es según el Host (con o sin puerto, en mayúsculas o no). */
export function sitioDe(host: string | null | undefined): Sitio {
	const h = (host ?? '').toLowerCase().replace(/:\d+$/, '').replace(/\.$/, '');
	return h.startsWith('lugares.') ? 'lugares' : 'caigo';
}

/** Rutas que existen igual en los dos hosts (el ingreso deja la cookie en el host donde se entró). */
const COMPARTIDAS = ['/ingresar', '/sso-callback', '/salir', '/archivo', '/terminos', '/privacidad'];

function esCompartida(ruta: string): boolean {
	return COMPARTIDAS.some((c) => ruta === c || ruta.startsWith(c + '/'));
}

/**
 * La ruta de src/routes que atiende un pedido. En lugares.* todo va a /panel salvo las compartidas;
 * en caigo.com.ar queda igual (y /panel no se puede pedir directo: lo corta hooks.server.ts).
 */
export function rutaInterna(host: string | null | undefined, ruta: string): string {
	if (sitioDe(host) !== 'lugares' || esCompartida(ruta)) return ruta;
	return ruta === '/' ? '/panel' : `/panel${ruta}`;
}

/** Si una ruta interna es del panel (para no servirla en caigo.com.ar). */
export function esDelPanel(rutaId: string | null | undefined): boolean {
	return !!rutaId && (rutaId === '/panel' || rutaId.startsWith('/panel/'));
}

/** Sólo vuelve a rutas propias: nada de //otro.sitio ni URLs absolutas. */
export function volverSeguro(v: string | null | undefined, porDefecto = '/'): string {
	if (!v || !v.startsWith('/') || v.startsWith('//') || v.startsWith('/\\')) return porDefecto;
	return v.length > 500 ? porDefecto : v;
}
