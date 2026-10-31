<script lang="ts">
	import Formulario from '../../../nuevo/Formulario.svelte';

	let { data, form } = $props();
</script>

{#key data.edicion.id}
	<Formulario {data} {form} edicion={data.edicion} />
{/key}
