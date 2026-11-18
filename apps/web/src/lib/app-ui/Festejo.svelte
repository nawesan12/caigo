<script lang="ts">
	import { PUBLIC_SITIO } from '$app/env/public';
	import Avatar from './Avatar.svelte';
	import { avisar } from '#lib/toast.svelte.ts';
	import { ics } from '#lib/gestos.ts';
	import type { Plan } from '#lib/tipos.ts';

	/*
	 * «¡Estás adentro!» (A2 del informe del 18/11, como el «It's a Match»): a pantalla completa con el
	 * gel lima, las caras de quienes van y la tuya entrando al hueco «+ Vos», y tres botones. Breve: se
	 * cierra con un toque. Sólo al entrar a un plan (nada de festejos por otras cosas).
	 */
	let {
		abierto = $bindable(false),
		plan,
		yo
	}: {
		abierto?: boolean;
		plan: Plan;
		yo: { id?: string; nombre: string; foto?: string };
	} = $props();

	const otros = $derived(plan.van.filter((v) => v.id !== yo.id).slice(0, 5));
	// Lo que vuelve el plan puede no traerme todavía: el número cuenta mi lugar.
	const somos = $derived(
		plan.van.some((v) => v.id === yo.id) ? plan.somos : Math.min(plan.total, plan.somos + 1)
	);
	const link = $derived(`${PUBLIC_SITIO.replace(/\/+$/, '')}/p/${plan.slug}`);

	function agendar() {
		const blob = new Blob([ics(plan, PUBLIC_SITIO)], { type: 'text/calendar' });
		const a = document.createElement('a');
		a.href = URL.createObjectURL(blob);
		a.download = `${plan.slug}.ics`;
		a.click();
		setTimeout(() => URL.revokeObjectURL(a.href), 1000);
	}

	async function avisarle() {
		try {
			if (navigator.share) return await navigator.share({ title: plan.titulo, url: link });
			await navigator.clipboard.writeText(link);
			avisar('Link copiado: ' + link.replace(/^https?:\/\//, ''));
		} catch {
			/* cancelado */
		}
	}

	function tecla(e: KeyboardEvent) {
		if (abierto && e.key === 'Escape') abierto = false;
	}
</script>

<svelte:window onkeydown={tecla} />

{#if abierto}
	<div class="festejo" role="dialog" aria-modal="true" aria-labelledby="festejo-t">
		<button class="cerrar" type="button" aria-label="Cerrar" onclick={() => (abierto = false)}
			><svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true"
				><path
					d="M6 6l12 12M18 6L6 18"
					stroke="currentColor"
					stroke-width="2.6"
					stroke-linecap="round"
				/></svg
			></button
		>
		<button
			class="fondo"
			type="button"
			aria-hidden="true"
			tabindex="-1"
			onclick={() => (abierto = false)}
		></button>
		<div class="centro">
			<h2 id="festejo-t">¡Estás adentro!</h2>
			<p class="plan">{plan.titulo}</p>
			<div class="caras">
				{#each otros as v, i (v.id || i)}
					<span class="cara" style:--i={i}><Avatar nombre={v.nombre} foto={v.foto} tam={56} /></span
					>
				{/each}
				<span class="cara vos" style:--i={otros.length}
					><Avatar nombre={yo.nombre} foto={yo.foto} tam={56} /><span class="mas">+ Vos</span></span
				>
			</div>
			<p class="somos">Somos {somos} de {plan.total}</p>
		</div>
		<div class="botones">
			<a class="gel gel-violeta grande" href="/app/plan/{plan.id}/chat">Saludar en el chat</a>
			<div class="dos">
				<button class="gel gel-blanco" type="button" onclick={agendar}>Agendar</button>
				<button class="gel gel-blanco" type="button" onclick={avisarle}>Avisarle a alguien</button>
			</div>
		</div>
	</div>
{/if}

<style>
	.festejo {
		position: absolute;
		inset: 0;
		z-index: 60;
		display: flex;
		flex-direction: column;
		justify-content: space-between;
		padding: calc(64px + env(safe-area-inset-top)) 24px calc(32px + env(safe-area-inset-bottom));
		background: var(--grad-lima);
		color: #24212d;
		animation: entra 0.25s ease both;
	}
	.fondo {
		position: absolute;
		inset: 0;
		border: 0;
		background: transparent;
		cursor: default;
	}
	.cerrar {
		position: absolute;
		top: calc(16px + env(safe-area-inset-top));
		right: 16px;
		z-index: 2;
		width: 44px;
		height: 44px;
		display: grid;
		place-items: center;
		border-radius: 50%;
		color: #24212d;
		background: rgba(255, 255, 255, 0.45);
	}
	.centro {
		position: relative;
		z-index: 1;
		pointer-events: none;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 12px;
		text-align: center;
		flex: 1;
		justify-content: center;
		padding-bottom: 24px;
	}
	h2 {
		font:
			800 40px/1 'Geist',
			sans-serif;
		letter-spacing: -1.4px;
	}
	.plan {
		font-size: 16px;
		font-weight: 600;
		text-wrap: balance;
	}
	.caras {
		display: flex;
		justify-content: center;
		margin: 18px 0 4px;
	}
	.cara {
		position: relative;
		margin-left: -12px;
		border-radius: 50%;
		box-shadow: 0 0 0 3px #d6fa80;
		animation: cae 0.4s cubic-bezier(0.2, 0.9, 0.3, 1.3) both;
		animation-delay: calc(var(--i) * 50ms);
	}
	.cara:first-child {
		margin-left: 0;
	}
	.vos {
		animation-name: vos;
		animation-delay: calc(var(--i) * 50ms + 120ms);
	}
	.mas {
		position: absolute;
		left: 50%;
		bottom: -24px;
		transform: translateX(-50%);
		font-size: 13px;
		font-weight: 800;
		white-space: nowrap;
	}
	.somos {
		margin-top: 22px;
		font-size: 18px;
		font-weight: 800;
	}
	.botones {
		position: relative;
		z-index: 1;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.grande {
		padding: 17px;
		border-radius: 20px;
		font-size: 17px;
	}
	.dos {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 10px;
	}
	.dos .gel {
		padding: 14px 8px;
		border-radius: 18px;
		font-size: 15px;
	}
	@keyframes entra {
		from {
			opacity: 0;
		}
	}
	@keyframes cae {
		from {
			opacity: 0;
			transform: translateY(-16px) scale(0.8);
		}
	}
	@keyframes vos {
		from {
			opacity: 0;
			transform: translateX(40px) scale(0.6);
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.festejo,
		.cara,
		.vos {
			animation: none;
		}
	}
</style>
