<script lang="ts">
	import { enhance } from '$app/forms';
	import CabeceraVolver from '#lib/app-ui/CabeceraVolver.svelte';
	import Hoja from '#lib/app-ui/Hoja.svelte';
	import Vacio from '#lib/ui/Vacio.svelte';
	import { avisar } from '#lib/toast.svelte.ts';
	import { cuandoCorto } from '#lib/fechas.ts';
	import { primerNombre } from '#lib/plan.ts';
	import type { FotoPlan } from '#lib/tipos.ts';

	/*
	 * Fotos del plan (informe del 18/11, B4). Sin pantalla en el handoff: la cabecera de Solicitudes,
	 * la grilla con el radio de las tarjetas, el botón lima y la hoja que sube para cada foto.
	 */
	let { data } = $props();
	const p = $derived(data.plan);
	let abierta = $state<FotoPlan | null>(null);
	let ver = $state(false);
	let subiendo = $state(false);
	let input = $state<HTMLInputElement>();

	const despues =
		(texto: string) =>
		() =>
		async ({
			result,
			update
		}: {
			result: { type: string; data?: Record<string, unknown> };
			update: () => Promise<void>;
		}) => {
			subiendo = false;
			ver = false;
			if (result.type === 'success') avisar(texto);
			else if (result.type === 'failure') avisar(String(result.data?.error ?? 'No se pudo.'));
			await update();
		};
</script>

<svelte:head><title>Fotos · {p.titulo} · Caigo</title></svelte:head>

<div class="scroll">
	<div class="contenido">
		<CabeceraVolver
			volver="/app/plan/{p.id}"
			titulo="Fotos del plan"
			sub="{p.titulo} · {cuandoCorto(p.empiezaEn)}"
		/>
		{#if data.cerrado}
			<Vacio titulo="Sólo para quienes fueron" texto={data.cerrado} />
		{:else}
			<p class="nota">Sólo las ven quienes fueron. Se borran a los 6&nbsp;meses.</p>
			{#if data.fotos.length}
				<div class="grilla">
					{#each data.fotos as f (f.id)}
						<button
							class="foto"
							onclick={() => {
								abierta = f;
								ver = true;
							}}
							aria-label="Foto de {primerNombre(f.autor.nombre)}"
						>
							<img src={f.url} alt="" loading="lazy" />
						</button>
					{/each}
				</div>
			{:else}
				<Vacio titulo="Todavía no hay fotos" texto="Subí la primera: la ven sólo los que fueron." />
			{/if}
			<form
				method="POST"
				action="?/subir"
				enctype="multipart/form-data"
				use:enhance={() => {
					subiendo = true;
					return despues('Foto subida.')();
				}}
			>
				<input
					bind:this={input}
					class="oculto"
					type="file"
					name="foto"
					accept="image/jpeg,image/png,image/webp,image/heic"
					onchange={(e) => (e.currentTarget as HTMLInputElement).form?.requestSubmit()}
				/>
				<button
					type="button"
					class="gel gel-lima subir"
					disabled={subiendo}
					onclick={() => input?.click()}>{subiendo ? 'Subiendo…' : 'Subir una foto'}</button
				>
			</form>
		{/if}
	</div>
</div>

<Hoja bind:abierta={ver} titulo={abierta ? `De ${primerNombre(abierta.autor.nombre)}` : 'Foto'}>
	{#if abierta}
		<img class="grande" src={abierta.url} alt="" />
		<form
			method="POST"
			action={abierta.mia ? '?/borrar' : '?/reportar'}
			use:enhance={despues(abierta.mia ? 'Foto borrada.' : 'Gracias. La revisamos.')}
		>
			<input type="hidden" name="foto" value={abierta.id} />
			<button class="accion">{abierta.mia ? 'Borrar mi foto' : 'Reportar esta foto'}</button>
		</form>
	{/if}
</Hoja>

<style>
	.contenido {
		padding: calc(20px + env(safe-area-inset-top)) 20px var(--bajo-tabbar, 40px);
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.nota {
		font-size: 13px;
		color: var(--texto-2);
	}
	.grilla {
		display: grid;
		grid-template-columns: repeat(3, 1fr);
		gap: 6px;
	}
	.foto {
		aspect-ratio: 1;
		border-radius: 14px;
		overflow: hidden;
		background: var(--lavanda);
		padding: 0;
	}
	.foto img {
		width: 100%;
		height: 100%;
		object-fit: cover;
		display: block;
	}
	.oculto {
		display: none;
	}
	.subir {
		width: 100%;
		height: 56px;
		border-radius: 20px;
		font-size: 17px;
	}
	.grande {
		width: 100%;
		max-height: 60vh;
		object-fit: contain;
		border-radius: 20px;
	}
	.accion {
		width: 100%;
		padding: 12px;
		font-size: 14px;
		font-weight: 700;
		color: var(--violeta-texto);
	}
</style>
