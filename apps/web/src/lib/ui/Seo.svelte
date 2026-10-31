<script lang="ts">
	import { PUBLIC_SITIO } from '$app/env/public';

	/** Título, descripción y Open Graph (para que el link se vea bien en WhatsApp). */
	let {
		titulo,
		descripcion = 'Hacé planes. Conocé gente. Sumate a planes a los que les falta gente en Mar del Plata y Villa Gesell.',
		imagen = '/fotos/grupo.jpg',
		ruta = '',
		noindex = false
	}: {
		titulo: string;
		descripcion?: string;
		imagen?: string;
		ruta?: string;
		noindex?: boolean;
	} = $props();

	const sitio = $derived(PUBLIC_SITIO.replace(/\/+$/, ''));
	const img = $derived(/^https?:\/\//.test(imagen) ? imagen : sitio + imagen);
</script>

<svelte:head>
	<title>{titulo}</title>
	<meta name="description" content={descripcion} />
	{#if noindex}<meta name="robots" content="noindex" />{/if}
	<meta property="og:type" content="website" />
	<meta property="og:site_name" content="Caigo" />
	<meta property="og:locale" content="es_AR" />
	<meta property="og:title" content={titulo} />
	<meta property="og:description" content={descripcion} />
	<meta property="og:image" content={img} />
	{#if ruta}<meta property="og:url" content={sitio + ruta} />{/if}
	<meta name="twitter:card" content="summary_large_image" />
</svelte:head>
