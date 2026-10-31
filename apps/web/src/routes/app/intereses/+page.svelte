<script lang="ts">
	import { enhance } from '$app/forms';
	import Icono from '#lib/ui/Icono.svelte';
	import { avisar } from '#lib/toast.svelte.ts';
	import { INTENCIONES, INTERESES_SUGERIDOS } from '#lib/app.ts';

	/*
	 * Después de verificarse, una vez: "¿Qué buscás en Caigo?" y "¿Qué te copa?" (pasos 1 y 2 del
	 * onboarding del Prototipo, `onb`). Se guarda en el perfil (intenciones e intereses).
	 */
	let { data, form } = $props();

	let paso = $state<1 | 2>(1);
	let intenciones = $state<string[]>([]);
	let intereses = $state<string[]>([]);
	let enviando = $state(false);

	/** Tile e ícono de cada intención, como INTENTS del Prototipo. */
	const ESTILO: Record<string, { tile: string; trazo: string; d: string }> = {
		planes: {
			tile: 'linear-gradient(180deg,#F0FFC2,#C8F36B 60%,#ABDB4B)',
			trazo: '#24212D',
			d: 'M12 4v16M4 12h16'
		},
		gente: {
			tile: 'linear-gradient(180deg,#B3A3FF,#7456F1 60%,#5F41E0)',
			trazo: '#FFFFFF',
			d: 'M9 4a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7zM2.5 20c1-3.5 3.5-5.5 6.5-5.5s5.5 2 6.5 5.5M16 4.5a3.5 3.5 0 0 1 0 7M18 14.5c2 .6 3.2 2.5 3.5 5.5'
		},
		completar: {
			tile: 'linear-gradient(180deg,#3D3756,#24212D)',
			trazo: '#C8F36B',
			d: 'M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM4 10h16M8 3v4M16 3v4'
		},
		citas: {
			tile: 'linear-gradient(180deg,#FFB2A6,#FF7868 60%,#F05E4C)',
			trazo: '#24212D',
			d: 'M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z'
		}
	};
	const alternar = (l: string[], v: string) =>
		l.includes(v) ? l.filter((x) => x !== v) : [...l, v];

	function seguir(e: Event) {
		if (paso === 2) return;
		e.preventDefault();
		if (!intenciones.length) return avisar('Elegí al menos una, después la cambiás.');
		paso = 2;
	}
</script>

<svelte:head><title>¿Qué buscás? · Caigo</title></svelte:head>

<div class="pant">
	{#if paso === 2}
		<button class="atras" onclick={() => (paso = 1)} aria-label="Volver"
			><Icono nombre="atras" tam={22} /></button
		>
	{/if}
	<div class="scroll-in">
		{#if paso === 1}
			<h1>¿Qué buscás en Caigo?</h1>
			<p>
				Elegí todas las que quieras. Armamos la app según esto y lo cambiás cuando&nbsp;quieras.
			</p>
			{#each INTENCIONES as it (it.valor)}
				{@const on = intenciones.includes(it.valor)}
				{@const e = ESTILO[it.valor]}
				<button
					class="intento"
					class:on
					aria-pressed={on}
					onclick={() => (intenciones = alternar(intenciones, it.valor))}
				>
					<span class="tile" style:background={e.tile}>
						<svg
							width="24"
							height="24"
							viewBox="0 0 24 24"
							fill="none"
							stroke={e.trazo}
							stroke-width="2.4"
							stroke-linecap="round"
							stroke-linejoin="round"
							aria-hidden="true"><path d={e.d} /></svg
						>
					</span>
					<span class="it-txt"><b>{it.etiqueta}</b><span>{it.texto}</span></span>
					{#if on}
						<span class="tilde"><Icono nombre="check" tam={16} color="#FFFFFF" trazo={3.4} /></span>
					{:else}
						<span class="caja"></span>
					{/if}
				</button>
			{/each}
		{:else}
			<h1>¿Qué te copa?</h1>
			<p>Te mostramos primero planes y gente con lo&nbsp;mismo.</p>
			<div class="likes">
				{#each INTERESES_SUGERIDOS as l (l)}
					<button
						class="like"
						aria-pressed={intereses.includes(l)}
						onclick={() => (intereses = alternar(intereses, l))}>{l}</button
					>
				{/each}
			</div>
		{/if}
	</div>

	<form
		method="POST"
		class="pie"
		use:enhance={() => {
			enviando = true;
			return async ({ result, update }) => {
				enviando = false;
				if (result.type === 'failure') avisar(String(result.data?.error ?? 'No se pudo.'));
				await update({ reset: false });
			};
		}}
	>
		<input type="hidden" name="volver" value={data.volver} />
		{#each intenciones as i (i)}<input type="hidden" name="intencion" value={i} />{/each}
		{#each intereses as i (i)}<input type="hidden" name="interes" value={i} />{/each}
		<div class="puntos" aria-hidden="true">
			{#each [0, 1, 2] as i (i)}<span class:on={paso === i}></span>{/each}
		</div>
		{#if form?.error}<span class="error" role="alert">{form.error}</span>{/if}
		<button class="gel gel-lima cta" onclick={seguir} disabled={enviando}
			>{paso === 1 ? 'Seguir' : 'Listo, mostrame Caigo'}</button
		>
	</form>
</div>

<style>
	.pant {
		position: absolute;
		inset: 0;
		z-index: 10;
		background: url(/grain.png), var(--blanco-calido);
	}
	.atras {
		position: absolute;
		top: calc(16px + env(safe-area-inset-top));
		left: 20px;
		z-index: 2;
		width: 44px;
		height: 44px;
		border-radius: 16px;
		display: grid;
		place-items: center;
		color: var(--carbon);
		background: var(--c-gel-blanco);
		box-shadow:
			inset 0 2px 0 #ffffff,
			0 3px 0 #dcd5ee;
	}
	.scroll-in {
		position: absolute;
		inset: 0;
		overflow-y: auto;
		padding: calc(70px + env(safe-area-inset-top)) 20px 160px;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	h1 {
		flex: none;
		font:
			800 34px/1.02 'Geist',
			sans-serif;
		letter-spacing: -1px;
	}
	p {
		flex: none;
		margin-bottom: 6px;
		font-size: 15px;
		line-height: 1.5;
		color: var(--texto-2);
		text-wrap: pretty;
	}
	.intento {
		flex: none;
		display: flex;
		align-items: center;
		gap: 14px;
		padding: 14px;
		border-radius: 24px;
		text-align: left;
		background: var(--c-tarjeta);
		box-shadow:
			inset 0 1px 0 #ffffff,
			0 3px 0 #e6e0f3,
			0 12px 22px -14px rgba(80, 60, 160, 0.3);
		transition: box-shadow 0.15s;
	}
	.intento.on {
		background: var(--lavanda);
		box-shadow:
			inset 0 0 0 2.5px var(--violeta),
			0 4px 0 #c9bef5,
			0 14px 24px -12px rgba(116, 86, 241, 0.5);
	}
	.tile {
		flex: none;
		width: 52px;
		height: 52px;
		border-radius: 18px;
		display: grid;
		place-items: center;
		box-shadow:
			inset 0 2px 1px rgba(255, 255, 255, 0.45),
			inset 0 -3px 0 rgba(0, 0, 0, 0.15);
	}
	.it-txt {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.it-txt b {
		font-size: 16px;
		font-weight: 800;
	}
	.it-txt span {
		font-size: 13px;
		font-weight: 500;
		line-height: 1.35;
		color: var(--texto-2);
		text-wrap: pretty;
	}
	.tilde,
	.caja {
		flex: none;
		width: 28px;
		height: 28px;
		border-radius: 10px;
	}
	.tilde {
		background: var(--violeta);
		display: grid;
		place-items: center;
	}
	.caja {
		border: 2px solid #d9d3ee;
	}
	.likes {
		display: flex;
		flex-wrap: wrap;
		gap: 9px;
	}
	.like {
		padding: 12px 16px;
		border-radius: 17px;
		font-size: 15px;
		font-weight: 700;
		color: var(--carbon);
		background: var(--grad-off);
		box-shadow: var(--sh-off);
	}
	.like[aria-pressed='true'] {
		color: #ffffff;
		background: var(--grad-violeta);
		box-shadow: var(--sh-sel);
	}
	.pie {
		position: absolute;
		left: 24px;
		right: 24px;
		bottom: calc(34px + env(safe-area-inset-bottom));
		display: flex;
		flex-direction: column;
		gap: 16px;
		align-items: center;
	}
	.puntos {
		display: flex;
		gap: 6px;
	}
	.puntos span {
		width: 8px;
		height: 8px;
		border-radius: 4px;
		background: #d9d3ee;
	}
	.puntos span.on {
		width: 26px;
		background: var(--violeta);
	}
	.cta {
		align-self: stretch;
		height: 62px;
		border-radius: 22px;
		font-size: 18px;
	}
	.error {
		font-size: 13px;
		font-weight: 700;
		color: #9a2e1f;
	}
</style>
