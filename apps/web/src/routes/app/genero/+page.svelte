<script lang="ts">
	import { untrack } from 'svelte';
	import { enhance } from '$app/forms';
	import Icono from '#lib/ui/Icono.svelte';

	/*
	 * Género, para los planes «Solo mujeres» (contrato §1). No está en el handoff: se arma como la
	 * verificación (01d) con los radios de 06c. La diversidad no se pregunta acá ni se guarda: se
	 * confirma al sumarse a un plan «Solo diversidades» (AAIP 1.2).
	 */
	let { data, form } = $props();

	let genero = $state(untrack(() => data.genero));
	let enviando = $state(false);
	const OPCIONES: [string, string][] = [
		['mujer', 'Mujer'],
		['varon', 'Varón'],
		['no_binario', 'No binario']
	];
	const volver = $derived(data.plan ? `/app/plan/${data.plan}` : '/app/perfil');
</script>

<svelte:head><title>Tu género · Caigo</title></svelte:head>

<div class="scroll">
	<form
		method="POST"
		class="pantalla"
		use:enhance={() => {
			enviando = true;
			return async ({ update }) => {
				await update({ reset: false });
				enviando = false;
			};
		}}
	>
		<div class="arriba">
			<a class="icono-btn" href={volver} aria-label="Volver"><Icono nombre="atras" tam={22} /></a>
		</div>
		<div class="txt">
			<h1>¿Con qué género te identificás?</h1>
			<p>Lo pedimos para los planes «Solo mujeres». No se muestra en tu perfil ni a&nbsp;nadie.</p>
		</div>

		<div class="radios" role="radiogroup" aria-label="Género">
			{#each OPCIONES as [k, l] (k)}
				<button
					type="button"
					class="radio"
					class:on={genero === k}
					role="radio"
					aria-checked={genero === k}
					onclick={() => (genero = k)}
				>
					<span class="anillo"></span><b>{l}</b>
				</button>
			{/each}
		</div>

		<input type="hidden" name="genero" value={genero} />
		<input type="hidden" name="plan" value={data.plan} />
		<input type="hidden" name="cantidad" value={data.pedido.cantidad} />
		<input type="hidden" name="modo" value={data.pedido.modo} />
		<input type="hidden" name="mensaje" value={data.pedido.mensaje} />

		{#if form?.error}<p class="error" role="alert">{form.error}</p>{/if}

		<div class="pie">
			{#if form && 'plan' in form && form.plan}
				<a class="gel gel-blanco cta" href="/app">Ver otros planes</a>
			{:else}
				<button class="gel gel-lima cta" disabled={enviando || !genero}
					>{enviando ? 'Guardando…' : data.plan ? 'Seguir y sumarme' : 'Guardar'}</button
				>
			{/if}
		</div>
	</form>
</div>

<style>
	.pantalla {
		min-height: 100%;
		padding: calc(20px + env(safe-area-inset-top)) 24px calc(36px + env(safe-area-inset-bottom));
		display: flex;
		flex-direction: column;
		gap: 24px;
	}
	.arriba {
		display: flex;
		margin-left: -4px;
	}
	.icono-btn {
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
		gap: 10px;
	}
	h1 {
		font:
			700 34px/1.05 'Geist',
			sans-serif;
		letter-spacing: -0.8px;
	}
	.txt p {
		font-size: 15px;
		line-height: 1.5;
		color: var(--texto-2);
	}
	.radios {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.radio {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 14px;
		border-radius: 18px;
		background: var(--c-tarjeta);
		border: 1.5px solid var(--c-borde);
		text-align: left;
		font-size: 16px;
		font-weight: 700;
		color: var(--carbon);
	}
	.radio.on {
		background: var(--grad-off);
		box-shadow: var(--sh-icono);
		border: 2px solid var(--violeta);
	}
	.anillo {
		flex: none;
		width: 22px;
		height: 22px;
		border-radius: 50%;
		border: 2px solid #cfc8e6;
		background: var(--c-tarjeta);
	}
	.on .anillo {
		border: 7px solid var(--violeta);
	}
	.error {
		padding: 12px 14px;
		border-radius: 16px;
		background: #ffe1db;
		color: #9a2e1f;
		font-size: 14px;
		font-weight: 700;
	}
	.pie {
		margin-top: auto;
		display: flex;
		flex-direction: column;
	}
	.cta {
		height: 60px;
		border-radius: 20px;
		font-size: 17px;
	}
</style>
