<script lang="ts">
	import CabeceraVolver from '#lib/app-ui/CabeceraVolver.svelte';
	import PlanTarjeta from '#lib/app-ui/PlanTarjeta.svelte';
	import Vacio from '#lib/ui/Vacio.svelte';
	import { diaCorto } from '#lib/dia.ts';

	/*
	 * Esta semana (informe del 18/11, B2). Sin pantalla en el handoff: la cabecera de segundo nivel, el
	 * contador oscuro del detalle, los chips de días de Explorar y las tarjetas de Ahora.
	 */
	let { data } = $props();
	const s = $derived(data.semana);
	const max = $derived(Math.max(1, ...s.dias.map((d) => d.cantidad)));
	/** Sólo las cifras que dicen algo: un cero grande hace parecer vacía la ciudad. */
	const numeros = $derived(
		[
			{ n: s.total, t: s.total === 1 ? 'plan' : 'planes' },
			{ n: s.aliados, t: 'aliados' },
			{ n: s.deTuGente, t: 'de tu gente' }
		].filter((x, i) => i === 0 || x.n > 0)
	);
	const DIAS_LARGOS: Record<string, string> = {
		Dom: 'domingo',
		Lun: 'lunes',
		Mar: 'martes',
		Mié: 'miércoles',
		Jue: 'jueves',
		Vie: 'viernes',
		Sáb: 'sábado'
	};
</script>

<svelte:head><title>Esta semana · Caigo</title></svelte:head>

<div class="scroll">
	<div class="contenido">
		<CabeceraVolver volver="/app" titulo="Esta semana" sub={s.ciudad} />
		<div class="numeros" style:grid-template-columns="repeat({numeros.length}, 1fr)">
			{#each numeros as x (x.t)}
				<div class="num"><b>{x.n}</b><span>{x.t}</span></div>
			{/each}
		</div>
		<div class="dias" style:grid-template-columns="repeat({s.dias.length}, 1fr)">
			{#each s.dias as d (d.dia)}
				{@const c = diaCorto(d.dia)}
				<a
					class="dia"
					href="/app/explorar?fecha={d.dia}"
					class:vacio={!d.cantidad}
					aria-label="{DIAS_LARGOS[c.nombre] ?? c.nombre} {c.numero}: {d.cantidad === 1
						? '1 plan'
						: `${d.cantidad} planes`}"
				>
					<span class="dn" aria-hidden="true">{c.nombre}</span>
					<span class="dd" aria-hidden="true">{c.numero}</span>
					<span class="barra" aria-hidden="true"
						><span style:height="{(d.cantidad / max) * 100}%"></span></span
					>
					<span class="dc" aria-hidden="true">{d.cantidad}</span>
				</a>
			{/each}
		</div>
		{#if s.planes.length}
			<h2>Lo mejor de la semana</h2>
			{#each s.planes as p (p.id)}
				<PlanTarjeta plan={p} />
			{/each}
		{:else}
			<Vacio titulo="Esta semana armá el tuyo" texto="Si publicás algo, sos lo primero que ven.">
				<a class="gel gel-lima" href="/app/crear">Armá un plan</a>
			</Vacio>
		{/if}
	</div>
</div>

<style>
	.contenido {
		padding: calc(20px + env(safe-area-inset-top)) 20px var(--bajo-tabbar);
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	.numeros {
		display: grid;
		grid-template-columns: repeat(3, 1fr);
		gap: 8px;
		padding: 16px;
		border-radius: 24px;
		background: var(--c-contador);
		outline: 1px solid var(--c-contador-borde);
		outline-offset: -1px;
	}
	.num {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.num b {
		font:
			800 34px/1 'Geist',
			sans-serif;
		letter-spacing: -1px;
		color: #c8f36b;
	}
	.num span {
		font-size: 12px;
		font-weight: 600;
		color: #faf9f6;
		text-wrap: balance;
	}
	.dias {
		display: grid;
		gap: 6px;
	}
	.dia {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 4px;
		padding: 8px 0;
		border-radius: 14px;
		background: var(--grad-off);
		box-shadow: var(--sh-icono);
		color: var(--carbon);
		text-decoration: none;
	}
	.dia.vacio {
		opacity: 0.55;
	}
	.dn {
		font-size: 11px;
		font-weight: 700;
		color: var(--texto-2);
	}
	.dd {
		font-size: 16px;
		font-weight: 800;
	}
	.barra {
		width: 8px;
		height: 36px;
		border-radius: 4px;
		background: var(--lavanda);
		display: flex;
		align-items: flex-end;
		overflow: hidden;
	}
	.barra span {
		width: 100%;
		border-radius: 4px;
		background: var(--violeta);
	}
	.dc {
		font-size: 12px;
		font-weight: 800;
	}
	h2 {
		font:
			700 20px 'Geist',
			sans-serif;
	}
</style>
