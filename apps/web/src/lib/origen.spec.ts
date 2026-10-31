import { describe, expect, it } from 'vitest';
import { origenDe } from './origen.ts';

describe('origen', () => {
	it('utm, ref y link de plan', () => {
		expect(origenDe(new URL('https://caigo.com.ar/?utm_source=Instagram Stories'))).toBe(
			'utm:instagram-stories'
		);
		expect(origenDe(new URL('https://caigo.com.ar/?ref=Lu'))).toBe('ref:lu');
		expect(origenDe(new URL('https://caigo.com.ar/p/truco-en-guemes-bpte'))).toBe(
			'p:truco-en-guemes-bpte'
		);
		expect(origenDe(new URL('https://caigo.com.ar/p/truco?utm_source=wa'))).toBe('utm:wa');
		expect(origenDe(new URL('https://caigo.com.ar/app'))).toBe('');
		expect(origenDe(new URL('https://caigo.com.ar/?ref=%F0%9F%98%80'))).toBe('');
	});
});
