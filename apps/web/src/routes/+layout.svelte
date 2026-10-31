<script lang="ts">
	import '#lib/app.css';
	import Toast from '#lib/ui/Toast.svelte';

	let { children } = $props();
</script>

{@render children()}
<Toast />
