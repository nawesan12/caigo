import { describe, expect, it } from 'vitest';
import { esDelPanel, rutaInterna, sitioDe, volverSeguro } from './hosts.ts';

describe('sitioDe', () => {
	it('reconoce el panel con o sin puerto', () => {
		expect(sitioDe('lugares.caigo.com.ar')).toBe('lugares');
		expect(sitioDe('LUGARES.caigo.com.ar.')).toBe('lugares');
		expect(sitioDe('lugares.localhost:5173')).toBe('lugares');
		expect(sitioDe('caigo.com.ar')).toBe('caigo');
		expect(sitioDe('localhost:5173')).toBe('caigo');
		expect(sitioDe('www.caigo.com.ar')).toBe('caigo');
		expect(sitioDe(null)).toBe('caigo');
	});
});

describe('rutaInterna', () => {
	it('en lugares.* todo va a /panel', () => {
		expect(rutaInterna('lugares.caigo.com.ar', '/')).toBe('/panel');
		expect(rutaInterna('lugares.caigo.com.ar', '/nuevo')).toBe('/panel/nuevo');
		expect(rutaInterna('lugares.localhost:5173', '/mercadopago/vuelta')).toBe(
			'/panel/mercadopago/vuelta'
		);
	});
	it('menos el ingreso y lo compartido', () => {
		expect(rutaInterna('lugares.caigo.com.ar', '/ingresar')).toBe('/ingresar');
		expect(rutaInterna('lugares.caigo.com.ar', '/sso-callback')).toBe('/sso-callback');
		expect(rutaInterna('lugares.caigo.com.ar', '/salir')).toBe('/salir');
		expect(rutaInterna('lugares.caigo.com.ar', '/archivo/abc')).toBe('/archivo/abc');
		// No alcanza con que empiece igual.
		expect(rutaInterna('lugares.caigo.com.ar', '/ingresarx')).toBe('/panel/ingresarx');
	});
	it('en caigo.com.ar queda igual', () => {
		expect(rutaInterna('caigo.com.ar', '/p/truco-guemes')).toBe('/p/truco-guemes');
		expect(rutaInterna('caigo.com.ar', '/')).toBe('/');
	});
});

describe('esDelPanel', () => {
	it('sólo las rutas de src/routes/panel', () => {
		expect(esDelPanel('/panel')).toBe(true);
		expect(esDelPanel('/panel/plan/[id]')).toBe(true);
		expect(esDelPanel('/panelx')).toBe(false);
		expect(esDelPanel('/p/[slug]')).toBe(false);
		expect(esDelPanel(null)).toBe(false);
	});
});

describe('volverSeguro', () => {
	it('sólo rutas propias', () => {
		expect(volverSeguro('/p/x?sumarme=1')).toBe('/p/x?sumarme=1');
		expect(volverSeguro('//evil.com')).toBe('/');
		expect(volverSeguro('/\\evil.com')).toBe('/');
		expect(volverSeguro('https://evil.com')).toBe('/');
		expect(volverSeguro('')).toBe('/');
		expect(volverSeguro(null, '/plus')).toBe('/plus');
	});
});
