<script lang="ts">
	import { PUBLIC_PANEL, PUBLIC_SITIO } from '$app/env/public';
	import { urlOgFija, OG_FIJAS } from '#lib/og.ts';
	import { ldTexto, type Ld } from '#lib/seo.ts';

	/**
	 * Título, descripción, canonical, Open Graph, tarjeta de X y datos estructurados de cada página.
	 * Títulos: "<Página> · Caigo" (la portada: "Caigo · Hacé planes. Conocé gente.").
	 * Las imágenes viven siempre en caigo.com.ar (también las del panel de lugares).
	 */
	let {
		titulo,
		descripcion = 'Sumate a planes a los que les falta gente en Mar del Plata y Villa Gesell, o completá los tuyos en un toque. Hacé planes y conocé gente con Caigo.',
		imagen = urlOgFija('caigo'),
		imagenAlt = OG_FIJAS.caigo.alt,
		ruta = '',
		tipo = 'website',
		lugares = false,
		noindex = false,
		ld = []
	}: {
		titulo: string;
		descripcion?: string;
		/** Ruta (/og/…, /p/…/og.png) o URL absoluta. 1200×630. */
		imagen?: string;
		imagenAlt?: string;
		/** La ruta canónica ("/plus"). Sin ruta, no hay canonical ni og:url. */
		ruta?: string;
		tipo?: 'website' | 'article';
		/** La página es de lugares.caigo.com.ar (canonical y og:url en ese host). */
		lugares?: boolean;
		noindex?: boolean;
		ld?: Ld | Ld[];
	} = $props();

	const sitio = $derived(PUBLIC_SITIO.replace(/\/+$/, ''));
	const base = $derived(lugares ? PUBLIC_PANEL.replace(/\/+$/, '') : sitio);
	const img = $derived(/^https?:\/\//.test(imagen) ? imagen : sitio + imagen);
	const canonica = $derived(ruta ? base + ruta : '');
	// El <script> de JSON-LD armado acá (ldTexto escapa <, > y &, así que no se puede cortar).
	const cierre = '<' + '/script>';
	const lds = $derived(
		(Array.isArray(ld) ? ld : [ld]).map(
			(x) => `<script type="application/ld+json">${ldTexto(x)}${cierre}`
		)
	);
</script>

<svelte:head>
	<title>{titulo}</title>
	<meta name="description" content={descripcion} />
	{#if noindex}
		<meta name="robots" content="noindex, follow" />
	{:else}
		<meta name="robots" content="index, follow, max-image-preview:large" />
	{/if}
	{#if canonica && !noindex}<link rel="canonical" href={canonica} />{/if}
	<meta property="og:type" content={tipo} />
	<meta property="og:site_name" content={lugares ? 'Caigo para lugares' : 'Caigo'} />
	<meta property="og:locale" content="es_AR" />
	<meta property="og:title" content={titulo} />
	<meta property="og:description" content={descripcion} />
	{#if canonica}<meta property="og:url" content={canonica} />{/if}
	<meta property="og:image" content={img} />
	<meta property="og:image:secure_url" content={img} />
	<meta property="og:image:type" content="image/png" />
	<meta property="og:image:width" content="1200" />
	<meta property="og:image:height" content="630" />
	<meta property="og:image:alt" content={imagenAlt} />
	<meta name="twitter:card" content="summary_large_image" />
	<meta name="twitter:title" content={titulo} />
	<meta name="twitter:description" content={descripcion} />
	<meta name="twitter:image" content={img} />
	<meta name="twitter:image:alt" content={imagenAlt} />
	{#each lds as script, i (i)}
		<!-- eslint-disable-next-line svelte/no-at-html-tags -- JSON escapado con ldTexto -->
		{@html script}
	{/each}
</svelte:head>
