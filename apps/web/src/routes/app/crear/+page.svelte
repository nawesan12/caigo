<script lang="ts">
	import FormPlan from '#lib/app-ui/FormPlan.svelte';

	/* Crear plan (Mockups v5, 06a–06c): el formulario está en FormPlan (también edita). */
	let { data, form } = $props();
</script>

<FormPlan yo={data.yo} centro={data.centro} {form} />
