<script lang="ts">
	import Deshacer from './Deshacer.svelte';
	import Festejo from './Festejo.svelte';
	import type { GestosLista } from './gestos-lista.svelte.ts';

	/** El «Deshacer» y el festejo de los gestos de una lista. */
	let { g, yo }: { g: GestosLista; yo: { id?: string; nombre: string; foto?: string } | null } =
		$props();
</script>

<Deshacer texto={g.deshacer?.texto ?? ''} ondeshacer={() => g.volver()} />
{#if g.festejo && yo}
	<Festejo bind:abierto={g.festejoAbierto} plan={g.festejo} {yo} />
{/if}
