declare global {
	namespace App {
		interface Locals {
			/** Qué host se pidió: caigo.com.ar o lugares.caigo.com.ar. */
			sitio: import('#lib/hosts.ts').Sitio;
			/** La sesión de Caigo (cs_…) de la cookie; null sin sesión. Nunca va al navegador. */
			token: string | null;
			/** La verificación de quien pide en /app (la lee hooks.server.ts una vez por pedido). */
			verif?: import('#lib/tipos.ts').Verificacion | null;
		}
		interface Error {
			message: string;
		}
	}
}

export {};
