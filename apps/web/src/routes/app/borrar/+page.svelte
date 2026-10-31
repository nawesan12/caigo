<script lang="ts">
	import { enhance } from '$app/forms';
	import Icono from '#lib/ui/Icono.svelte';

	/*
	 * Borrar la cuenta. No está en el handoff: se arma con la cabecera de Solicitudes, una tarjeta
	 * blanca y los botones de la casa. Lo piden App Store y Play (y Play, también desde la web).
	 */
	let { form } = $props();
	let enviando = $state(false);
</script>

<svelte:head><title>Borrar mi cuenta · Caigo</title></svelte:head>

<div class="scroll">
	<div class="contenido">
		<div class="cab">
			<a class="icono-btn" href="/app/perfil" aria-label="Volver al perfil"
				><Icono nombre="atras" tam={22} /></a
			>
			<span class="t">Borrar mi cuenta</span>
		</div>

		<div class="tarjeta">
			<span class="tt">¿Borrar tu cuenta?</span>
			<p>
				Se borran tu perfil, tus fotos, tu selfie, tu DNI y tu teléfono, y se cancelan tus planes
				que no pasaron.
			</p>
			<p>
				Si tenés Caigo Plus, cancelalo en la tienda donde lo compraste (App Store, Google Play o
				Mercado&nbsp;Pago).
			</p>
		</div>

		{#if form?.error}<p class="error" role="alert">{form.error}</p>{/if}

		<form
			method="POST"
			use:enhance={() => {
				enviando = true;
				return async ({ update }) => {
					await update();
					enviando = false;
				};
			}}
		>
			<button class="borrar" disabled={enviando}>Borrar mi cuenta</button>
			<a class="gel gel-blanco grande" href="/app/perfil">Cancelar</a>
		</form>
	</div>
</div>

<style>
	.contenido {
		padding: calc(20px + env(safe-area-inset-top)) 20px 40px;
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
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
	.t {
		font:
			700 22px/1.1 'Geist',
			sans-serif;
	}
	.tarjeta {
		display: flex;
		flex-direction: column;
		gap: 10px;
		padding: 18px;
		border-radius: 22px;
		background: var(--c-tarjeta);
		box-shadow: var(--sh-tarjeta-plana);
	}
	.tt {
		font-size: 18px;
		font-weight: 800;
	}
	.tarjeta p {
		margin: 0;
		font-size: 14px;
		font-weight: 600;
		line-height: 1.4;
		color: var(--texto-2);
	}
	form {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.grande {
		height: 58px;
		border-radius: 20px;
		font-size: 17px;
	}
	.borrar {
		height: 58px;
		border: 0;
		border-radius: 20px;
		font: inherit;
		font-size: 17px;
		font-weight: 800;
		color: #ffffff;
		cursor: pointer;
		background: linear-gradient(180deg, #ffb2a6 0%, #ff7868 40%, #f05e4c 100%);
		box-shadow:
			inset 0 3px 2px rgba(255, 255, 255, 0.45),
			0 4px 0 #d04a3a;
	}
	.borrar:disabled {
		opacity: 0.6;
	}
	.error {
		margin: 0;
		font-size: 14px;
		font-weight: 700;
		color: #d04a3a;
	}
</style>
