import type { Reroute } from '@sveltejs/kit/hooks';
import { rutaInterna } from '#lib/hosts.ts';

/** lugares.caigo.com.ar/… lo atienden las rutas de src/routes/panel (ver lib/hosts.ts). */
export const reroute: Reroute = ({ url }) => rutaInterna(url.host, url.pathname);
