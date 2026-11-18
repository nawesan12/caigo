<script lang="ts">
	import { enhance } from '$app/forms';
	import CabeceraVolver from '#lib/app-ui/CabeceraVolver.svelte';
	import { avisar } from '#lib/toast.svelte.ts';

	/*
	 * «Traé a alguien». Sin pantalla en el handoff: se arma con la caja, el input y los geles del
	 * perfil. El premio cuenta recién con el primer plan del invitado (contra las cuentas falsas).
	 */
	let { data, form } = $props();
	const inv = $derived(data.inv);
	const corto = $derived(inv.link.replace(/^https?:\/\//, ''));
	let enviando = $state(false);

	async function compartir() {
		try {
			if (navigator.share)
				return await navigator.share({ title: 'Caigo', text: 'Sumate a Caigo', url: inv.link });
			await navigator.clipboard.writeText(inv.link);
			avisar('Link copiado.');
		} catch {
			/* cancelado */
		}
	}
</script>

<svelte:head><title>Traé a alguien · Caigo</title></svelte:head>

<div class="scroll">
	<div class="contenido">
		<CabeceraVolver volver="/app/perfil" titulo="Traé a alguien" etiqueta="Volver al perfil" />

		<div class="caja">
			<p class="t">Cuando vaya a su primer plan, los dos tienen una semana de&nbsp;Plus.</p>
			<span class="link">{corto}</span>
			<button class="gel gel-lima grande" type="button" onclick={compartir}
				>Compartir mi link</button
			>
			{#if inv.invitados}
				<span class="s"
					>{inv.invitados}
					{inv.invitados === 1 ? 'invitado' : 'invitados'} · {inv.premiados} ya&nbsp;fueron</span
				>
			{/if}
		</div>

		{#if inv.puedeUsarCodigo && !form?.listo}
			<form
				class="caja"
				method="POST"
				action="?/codigo"
				use:enhance={() => {
					enviando = true;
					return async ({ update }) => {
						await update({ reset: false });
						enviando = false;
					};
				}}
			>
				<label class="t" for="codigo">¿Te invitó alguien? Poné su&nbsp;código</label>
				<input
					id="codigo"
					class="input"
					name="codigo"
					maxlength="20"
					autocomplete="off"
					autocapitalize="off"
					placeholder="Ej: k3f9m2pq"
				/>
				{#if form?.error}<p class="error" role="alert">{form.error}</p>{/if}
				<button class="gel gel-violeta grande" disabled={enviando}>Listo</button>
			</form>
		{:else if form?.listo}
			<p class="s ok" role="status">Listo. El premio llega con tu primer&nbsp;plan.</p>
		{/if}
	</div>
</div>

<style>
	.contenido {
		padding: calc(20px + env(safe-area-inset-top)) 20px 40px;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.contenido > :global(.cab) {
		margin-bottom: 6px;
	}
	.caja {
		display: flex;
		flex-direction: column;
		gap: 12px;
		padding: 18px;
		border-radius: 24px;
		background: var(--c-tarjeta);
		box-shadow: var(--sh-tarjeta-plan);
	}
	.t {
		font-size: 16px;
		font-weight: 700;
		line-height: 1.3;
		text-wrap: pretty;
	}
	.link {
		padding: 12px 14px;
		border-radius: 14px;
		background: var(--input);
		font-size: 14px;
		font-weight: 600;
		color: var(--violeta-texto);
		overflow-wrap: anywhere;
	}
	.s {
		font-size: 13px;
		color: var(--texto-2);
	}
	.ok {
		text-align: center;
	}
	.grande {
		padding: 15px;
		border-radius: 18px;
		font-size: 16px;
	}
	.error {
		font-size: 13px;
		font-weight: 600;
		color: #c23a28;
	}
</style>
