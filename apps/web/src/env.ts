import { defineEnvVars } from '@sveltejs/kit/env';

/** Una variable opcional: vacía o sin definir vale `porDefecto`. */
const opcional = (porDefecto: string) => (v: string | undefined) => (v ?? '').trim() || porDefecto;

export const variables = defineEnvVars({
	CAIGO_API: {
		description:
			'URL interna de la API de Caigo (Go). En el VPS http://127.0.0.1:4300; en local http://localhost:4300.',
		schema: opcional('http://localhost:4300')
	},
	PUBLIC_CLERK_PUBLICABLE: {
		public: true,
		description:
			'Clave publicable de Clerk (pk_live_… de la instancia de Grupo Vimo). Vacía: sin ingreso.',
		schema: opcional('')
	},
	PUBLIC_SITIO: {
		public: true,
		description: 'URL pública de Caigo (https://caigo.com.ar).',
		schema: opcional('https://caigo.com.ar')
	},
	PUBLIC_PANEL: {
		public: true,
		description: 'URL pública del panel de lugares (https://lugares.caigo.com.ar).',
		schema: opcional('https://lugares.caigo.com.ar')
	},
	PUBLIC_MAPA_ESTILO: {
		public: true,
		description:
			'URL del estilo de MapLibre para el mapa del registro de lugares. Vacía: teselas raster estándar de OpenStreetMap.',
		schema: opcional('')
	}
});
