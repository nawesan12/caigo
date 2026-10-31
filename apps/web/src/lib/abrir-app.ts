/**
 * "Abrir en la app" desde /p y /t. Esas URLs (https://caigo.com.ar/p/… y /t/…) son enlaces
 * universales: si la app está instalada, el sistema la abre antes de llegar a la web. Si igual se
 * llegó acá, en iPhone el banner de Safari (apple-itunes-app) la abre o lleva a la App Store; el
 * botón va a la tienda. En Android, un intent con esquema https (el mismo App Link) abre la app y,
 * si no está, Play Store. En la compu, a la landing con las tiendas.
 */
export const APP_STORE = 'https://apps.apple.com/ar/app/caigo/id6829274265';
export const APP_STORE_ID = '6829274265';
export const PLAY_STORE = 'https://play.google.com/store/apps/details?id=ar.com.caigo.app';

export type Sistema = 'ios' | 'android' | 'otro';

export function sistemaDe(ua: string | null | undefined): Sistema {
	const u = ua ?? '';
	if (/android/i.test(u)) return 'android';
	// iPadOS se presenta como Mac: se reconoce por el touch en el cliente, acá alcanza con esto.
	if (/iphone|ipad|ipod/i.test(u)) return 'ios';
	return 'otro';
}

/** El href del botón para `url` (la URL https completa de esta página). */
export function abrirEnLaApp(url: string, sistema: Sistema): string {
	if (sistema === 'ios') return APP_STORE;
	if (sistema === 'android') {
		const u = new URL(url);
		return (
			`intent://${u.host}${u.pathname}${u.search}#Intent;scheme=https;package=ar.com.caigo.app;` +
			`S.browser_fallback_url=${encodeURIComponent(PLAY_STORE)};end`
		);
	}
	return '/#app';
}
