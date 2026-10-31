import { defineConfig } from 'vitest/config';
import adapter from '@sveltejs/adapter-node';
import { sveltekit } from '@sveltejs/kit/vite';

export default defineConfig({
	plugins: [
		sveltekit({
			compilerOptions: {
				runes: ({ filename }) =>
					filename.split(/[/\\]/).includes('node_modules') ? undefined : true,
				experimental: { async: true }
			},
			adapter: adapter({ precompress: true })
		})
	],
	// El WASM de resvg (imágenes OG) se importa con ?inline como cualquier otro recurso.
	assetsInclude: ['**/*.wasm'],
	// lugares.localhost también tiene que llegar al servidor de desarrollo.
	server: { allowedHosts: ['localhost', 'lugares.localhost'] },
	test: {
		expect: { requireAssertions: true },
		include: ['src/**/*.{test,spec}.{js,ts}'],
		environment: 'node'
	}
});
