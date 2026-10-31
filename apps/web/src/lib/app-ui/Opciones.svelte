<script lang="ts" module>
	export type Opcion = { texto: string; href?: string; accion?: () => void; peligro?: boolean };
</script>

<script lang="ts">
	import Hoja from './Hoja.svelte';

	/*
	 * Menú de opciones (el del chat): la hoja de abajo con una tarjeta de filas, separadas con la
	 * línea lavanda como «Herramientas para salir tranqui» de 06b. Lo que reporta, cancela o bloquea
	 * va en coral, igual que en las apps.
	 */
	let {
		abierta = $bindable(false),
		titulo = 'Opciones',
		opciones
	}: { abierta?: boolean; titulo?: string; opciones: Opcion[] } = $props();

	function elegir(o: Opcion) {
		abierta = false;
		o.accion?.();
	}
</script>

<Hoja bind:abierta {titulo}>
	<div class="filas">
		{#each opciones as o (o.texto)}
			{#if o.href}
				<a class="fila" class:peligro={o.peligro} href={o.href} onclick={() => (abierta = false)}
					>{o.texto}</a
				>
			{:else}
				<button class="fila" class:peligro={o.peligro} onclick={() => elegir(o)}>{o.texto}</button>
			{/if}
		{/each}
	</div>
</Hoja>

<style>
	.filas {
		display: flex;
		flex-direction: column;
		border-radius: 24px;
		overflow: hidden;
		background: var(--c-tarjeta);
		box-shadow: var(--sh-tarjeta-plana);
	}
	.fila {
		display: block;
		width: 100%;
		padding: 16px 18px;
		text-align: left;
		font-size: 15px;
		font-weight: 700;
		color: var(--carbon);
		text-decoration: none;
	}
	.fila + .fila {
		border-top: 1px solid var(--c-linea);
	}
	.peligro {
		color: #f05e4c;
	}
</style>
