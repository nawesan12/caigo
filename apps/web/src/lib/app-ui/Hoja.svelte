<script lang="ts">
	import type { Snippet } from 'svelte';

	/*
	 * Hoja que sube desde abajo, dentro del teléfono. Se arma con piezas del handoff: el velo
	 * rgba(32,29,43,.6) de los overlays del Prototipo y la hoja blanca cálida con radio 32 de arriba
	 * (la misma del detalle y de 09b). Se cierra tocando el velo o con Escape.
	 */
	let {
		abierta = $bindable(false),
		titulo,
		children
	}: { abierta?: boolean; titulo: string; children: Snippet } = $props();

	function tecla(e: KeyboardEvent) {
		if (abierta && e.key === 'Escape') abierta = false;
	}
</script>

<svelte:window onkeydown={tecla} />

{#if abierta}
	<div class="velo" role="presentation" onclick={() => (abierta = false)}></div>
	<div class="hoja" role="dialog" aria-modal="true" aria-label={titulo}>
		<span class="tirador" aria-hidden="true"></span>
		<h2>{titulo}</h2>
		{@render children()}
	</div>
{/if}

<style>
	.velo {
		position: absolute;
		inset: 0;
		z-index: 40;
		background: rgba(32, 29, 43, 0.6);
		animation: aparece 0.2s ease both;
	}
	.hoja {
		position: absolute;
		left: 0;
		right: 0;
		bottom: 0;
		z-index: 41;
		max-height: 88%;
		overflow-y: auto;
		border-radius: 32px 32px 0 0;
		background: var(--blanco-calido);
		padding: 12px 20px calc(30px + env(safe-area-inset-bottom));
		display: flex;
		flex-direction: column;
		gap: 14px;
		animation: sube 0.25s ease both;
	}
	.tirador {
		align-self: center;
		width: 40px;
		height: 5px;
		border-radius: 3px;
		background: #e4defa;
	}
	h2 {
		font:
			700 22px/1.15 'Geist',
			sans-serif;
	}
	@keyframes aparece {
		from {
			opacity: 0;
		}
	}
	@keyframes sube {
		from {
			transform: translateY(40px);
			opacity: 0;
		}
	}
</style>
