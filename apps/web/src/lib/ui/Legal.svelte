<script lang="ts">
	import type { Snippet } from 'svelte';
	import { PUBLIC_SITIO } from '$app/env/public';
	import Seo from './Seo.svelte';
	import Pie from './Pie.svelte';
	import { ldMigas } from '#lib/seo.ts';

	/** Páginas legales: la columna del link público y las tarjetas neutras del panel. */
	let {
		titulo,
		ruta,
		vigencia,
		descripcion,
		children
	}: {
		titulo: string;
		ruta: string;
		vigencia?: string;
		/** La meta description (140–160 caracteres). */
		descripcion: string;
		children: Snippet;
	} = $props();
	/** Sin los espacios duros del título visible. */
	const nombre = $derived(titulo.replace(/\u00a0/g, ' '));
</script>

<Seo
	titulo="{nombre} · Caigo"
	{descripcion}
	{ruta}
	tipo="article"
	ld={ldMigas(PUBLIC_SITIO.replace(/\/+$/, ''), [
		['Caigo', '/'],
		[nombre, ruta]
	])}
/>

<div class="fondo">
	<div class="col">
		<a class="marca" href="/">Caigo</a>
		{#if vigencia}<span class="vigencia">Vigente desde el {vigencia}</span>{/if}
		<h1>{titulo}</h1>
		<div class="tarjeta-plana texto">{@render children()}</div>
	</div>
</div>
<Pie />

<style>
	.fondo {
		background:
			url(/grain.png), radial-gradient(90% 38% at 100% 0%, #ece5ff 0%, rgba(236, 229, 255, 0) 70%),
			#faf9f6;
		padding: 20px 16px 64px;
	}
	.col {
		max-width: 720px;
		margin: 0 auto;
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		gap: 14px;
	}
	.marca {
		font:
			700 30px/1 'Geist',
			sans-serif;
		letter-spacing: -0.045em;
		color: #24212d;
		text-decoration: none;
		margin-bottom: 20px;
	}
	.vigencia {
		padding: 6px 11px;
		border-radius: 12px;
		background: #f0ecff;
		color: #5b43c9;
		font-size: 12px;
		font-weight: 800;
	}
	h1 {
		font:
			800 clamp(32px, 9.6vw, 40px) / 1 'Geist',
			sans-serif;
		letter-spacing: -1.4px;
	}
	.texto {
		align-self: stretch;
		padding: 24px;
		border-radius: 28px;
		font-size: 15px;
		line-height: 1.6;
		color: #24212d;
	}
	.texto :global(h2) {
		font:
			700 20px/1.2 'Geist',
			sans-serif;
		margin: 18px 0 6px;
	}
	.texto :global(h2:first-child) {
		margin-top: 0;
	}
	.texto :global(p),
	.texto :global(li) {
		color: #5c586b;
	}
	.texto :global(p + p) {
		margin-top: 10px;
	}
	.texto :global(ul) {
		padding-left: 20px;
		margin: 6px 0;
	}
</style>
