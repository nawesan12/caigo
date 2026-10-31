<script lang="ts">
	import Icono from '#lib/ui/Icono.svelte';

	/** Cabecera de las pantallas de segundo nivel (la de Solicitudes): volver + título. */
	let {
		volver,
		titulo,
		sub = '',
		etiqueta = 'Volver'
	}: { volver: string; titulo: string; sub?: string; etiqueta?: string } = $props();
</script>

<div class="cab">
	<a class="icono-btn" href={volver} aria-label={etiqueta}><Icono nombre="atras" tam={22} /></a>
	<div class="txt">
		<span class="t">{titulo}</span>
		{#if sub}<span class="s">{sub}</span>{/if}
	</div>
</div>

<style>
	.cab {
		display: flex;
		align-items: center;
		gap: 12px;
	}
	.icono-btn {
		flex: none;
		width: 44px;
		height: 44px;
		border-radius: 16px;
		display: grid;
		place-items: center;
		color: var(--carbon);
		background: var(--grad-off);
		box-shadow: var(--sh-icono);
	}
	.txt {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}
	.t {
		font:
			700 22px/1.1 'Geist',
			sans-serif;
	}
	.s {
		font-size: 13px;
		color: var(--texto-2);
	}
</style>
