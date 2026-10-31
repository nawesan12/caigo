<script lang="ts">
	import { page } from '$app/state';
	import Seo from '#lib/ui/Seo.svelte';
	import Vacio from '#lib/ui/Vacio.svelte';

	const noEsta = $derived(page.status === 404);
</script>

<Seo titulo="{noEsta ? 'No está' : 'Algo falló'} · Caigo" noindex />

<div class="fondo">
	<Vacio
		titulo={noEsta ? 'Acá no hay nada.' : 'Algo no salió bien.'}
		texto={page.error?.message && page.error.message !== 'Not Found'
			? page.error.message
			: noEsta
				? 'Ese link no existe o el plan es privado.'
				: 'Probá de nuevo en un rato.'}
	>
		<a class="gel gel-lima" href="/">Ir a Caigo</a>
	</Vacio>
</div>

<style>
	.fondo {
		min-height: 100vh;
		min-height: 100dvh;
		display: grid;
		place-items: center;
		padding: 40px 28px;
		background:
			url(/grain.png), radial-gradient(90% 38% at 100% 0%, #ece5ff 0%, rgba(236, 229, 255, 0) 70%),
			#faf9f6;
	}
</style>
