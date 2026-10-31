<script lang="ts">
	import Formulario from './Formulario.svelte';

	let { data, form } = $props();
</script>

<Formulario {data} {form} />
