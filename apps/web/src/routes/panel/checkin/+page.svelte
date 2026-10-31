<script lang="ts">
	import Vacio from '#lib/ui/Vacio.svelte';
</script>

<svelte:head><title>Check-in de hoy · Caigo para lugares</title></svelte:head>

<div class="vacio">
	<Vacio titulo="Hoy no hay planes" texto="Cuando publiques uno, el check-in en la puerta se hace desde acá.">
		<a class="gel gel-lima" href="/nuevo">Publicar plan</a>
	</Vacio>
</div>

<style>
	.vacio {
		padding: 60px 0;
	}
</style>
