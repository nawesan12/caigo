<script lang="ts">
	import Icono from './Icono.svelte';

	/**
	 * Los requisitos de la contraseña nueva, que se tildan solos mientras se escribe (calcado de Tengo
	 * Libre). No está en el handoff: el tilde de la casa sobre el grafito del ingreso.
	 */
	let { req }: { req: { largo: boolean; comun: boolean } } = $props();
</script>

<ul aria-live="polite">
	<li class:ok={req.largo}>
		<span class="marca"
			>{#if req.largo}<Icono nombre="check" tam={14} trazo={3} />{/if}</span
		>8 caracteres o más
	</li>
	<li class:ok={req.comun}>
		<span class="marca"
			>{#if req.comun}<Icono nombre="check" tam={14} trazo={3} />{/if}</span
		>No es una de las más&nbsp;usadas
	</li>
</ul>

<style>
	ul {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 8px;
		font-size: 14px;
		font-weight: 600;
		color: #c9c4da;
	}
	li {
		display: flex;
		align-items: center;
		gap: 10px;
	}
	.marca {
		flex: none;
		width: 20px;
		height: 20px;
		border-radius: 50%;
		display: grid;
		place-items: center;
		box-shadow: inset 0 0 0 1.5px rgba(255, 255, 255, 0.3);
		color: #24212d;
	}
	.ok {
		color: #faf9f6;
	}
	.ok .marca {
		background: #c8f36b;
		box-shadow: none;
	}
</style>
