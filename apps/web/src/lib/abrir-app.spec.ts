import { describe, expect, it } from 'vitest';
import { abrirEnLaApp, APP_STORE, sistemaDe } from './abrir-app.ts';

describe('abrir en la app', () => {
	it('reconoce el sistema', () => {
		expect(sistemaDe('Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)')).toBe('ios');
		expect(sistemaDe('Mozilla/5.0 (Linux; Android 14; Pixel 8)')).toBe('android');
		expect(sistemaDe('Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0)')).toBe('otro');
		expect(sistemaDe(null)).toBe('otro');
	});
	it('arma el enlace de cada sistema', () => {
		const url = 'https://caigo.com.ar/t/abc123?picc_data=AA&cmac=BB';
		expect(abrirEnLaApp(url, 'ios')).toBe(APP_STORE);
		const a = abrirEnLaApp(url, 'android');
		expect(a).toMatch(
			/^intent:\/\/caigo\.com\.ar\/t\/abc123\?picc_data=AA&cmac=BB#Intent;scheme=https;/
		);
		expect(a).toContain('package=ar.com.caigo.app');
		expect(a).toContain('S.browser_fallback_url=https%3A%2F%2Fplay.google.com');
		expect(abrirEnLaApp(url, 'otro')).toBe('/#app');
	});
});
