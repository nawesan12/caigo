<script lang="ts">
	import { onMount } from 'svelte';
	import Icono from '#lib/ui/Icono.svelte';
	import { cuentaRegresiva } from '#lib/dia.ts';
	import type { Plan } from '#lib/tipos.ts';

	/*
	 * El plan de hoy, fijo arriba de Ahora y de Mis planes (6.8 del informe): la cuenta regresiva y las
	 * dos cosas del día, «Llegué» y «¿Todo bien?», a un toque. Es el contador oscuro de DiaDelPlan en
	 * chico; los botones abren el detalle con la acción ya en marcha.
	 */
	let { plan: p }: { plan: Plan } = $props();

	let ahora = $state(new Date());
	onMount(() => {
		const t = setInterval(() => (ahora = new Date()), 30_000);
		return () => clearInterval(t);
	});
	const falta = $derived(cuentaRegresiva(p.empiezaEn, ahora));
	const llegue = $derived(!!p.mio?.llegue);
</script>

<section class="hoy" aria-label="Tu plan de hoy">
	<a class="cab" href="/app/plan/{p.id}">
		<span class="et">Hoy</span>
		<span class="titulo">{p.titulo}</span>
		<span class="falta">{falta}</span>
	</a>
	<div class="dos">
		{#if llegue}
			<span class="gel gel-lima boton hecho"
				><Icono nombre="check" tam={18} color="#24212D" trazo={3.4} />Llegaste</span
			>
		{:else}
			<a class="gel gel-lima boton" href="/app/plan/{p.id}?llegue=1"
				><Icono nombre="check" tam={18} color="#24212D" trazo={3.4} />Llegué</a
			>
		{/if}
		<a class="gel gel-blanco boton" href="/app/plan/{p.id}?todobien=1"
			><Icono nombre="escudo" tam={18} trazo={2.2} />¿Todo bien?{#if p.mio?.todoBien}<span
					class="on">Activo</span
				>{/if}</a
		>
	</div>
</section>

<style>
	.hoy {
		margin: 0 20px 16px;
		padding: 14px;
		border-radius: 24px;
		background: var(--c-contador);
		outline: 1px solid var(--c-contador-borde);
		outline-offset: -1px;
		box-shadow:
			inset 0 1px 0 rgba(255, 255, 255, 0.08),
			0 12px 26px -10px rgba(36, 33, 45, 0.55);
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.cab {
		display: grid;
		grid-template-columns: 1fr auto;
		grid-template-areas: 'et falta' 'titulo falta';
		align-items: center;
		column-gap: 12px;
		color: #faf9f6;
		text-decoration: none;
	}
	.et {
		grid-area: et;
		font-size: 12px;
		font-weight: 800;
		letter-spacing: 0.6px;
		text-transform: uppercase;
		color: #c8f36b;
	}
	.titulo {
		grid-area: titulo;
		min-width: 0;
		font-size: 16px;
		font-weight: 700;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.falta {
		grid-area: falta;
		font:
			800 22px/1 'Geist',
			sans-serif;
		letter-spacing: -0.6px;
		color: #c8f36b;
		white-space: nowrap;
	}
	.dos {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 10px;
	}
	.boton {
		height: 48px;
		border-radius: 16px;
		font-size: 15px;
		gap: 6px;
		white-space: nowrap;
	}
	.hecho {
		pointer-events: none;
	}
	.on {
		margin-left: 2px;
		padding: 2px 6px;
		border-radius: 8px;
		background: #c8f36b;
		color: #24212d;
		font-size: 11px;
		font-weight: 800;
	}
</style>
