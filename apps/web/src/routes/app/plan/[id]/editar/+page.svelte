<script lang="ts">
	import FormPlan from '#lib/app-ui/FormPlan.svelte';

	/* Editar plan: los pasos de crear (06a–06c) con lo que el plan ya tiene. */
	let { data, form } = $props();
</script>

<FormPlan yo={data.yo} centro={data.centro} {form} plan={data.plan} />
