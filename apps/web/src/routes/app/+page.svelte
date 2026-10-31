<script lang="ts">
	import { onMount } from 'svelte';
	import Icono from '#lib/ui/Icono.svelte';
	import PlanTarjeta from '#lib/app-ui/PlanTarjeta.svelte';
	import Vacio from '#lib/ui/Vacio.svelte';
	import { FILTROS_AHORA } from '#lib/app.ts';
	import { pedirUbicacion } from '#lib/app-ui/ubicacion.ts';

	/* Ahora · lista (Mockups v5, pantalla 02); sin planes, el estado vacío 13a. */
	let { data } = $props();

	const lugar = $derived(
		[data.yo.zona, data.yo.ciudad].filter(Boolean).join(', ') || 'Mar del Plata'
	);
	const q = (c: string) => (c ? `?categoria=${c}` : '');

	onMount(() => {
		if (!data.ubic) pedirUbicacion();
	});
</script>

<svelte:head><title>Ahora · Caigo</title></svelte:head>

<div class="scroll">
	<div class="arriba">
		<button class="donde" onclick={() => pedirUbicacion()} aria-label="Usar mi ubicación">
			<Icono nombre="pin" tam={16} color="#7456F1" />{lugar}<Icono
				nombre="abajo"
				tam={14}
				color="#5C586B"
				trazo={2.4}
			/>
		</button>
		<div class="titulo-fila">
			<h1>Ahora</h1>
			<nav class="seg-app" aria-label="Vista">
				<a href="/app{q(data.categoria)}" aria-current="page">Lista</a>
				<a href="/app/mapa{q(data.categoria)}">Mapa</a>
			</nav>
		</div>
		<div class="filtros">
			{#each FILTROS_AHORA as f (f.valor)}
				<a
					class="filtro"
					class:on={data.categoria === f.valor}
					href="/app{q(f.valor)}"
					data-sveltekit-noscroll
					aria-current={data.categoria === f.valor ? 'true' : undefined}>{f.etiqueta}</a
				>
			{/each}
		</div>
	</div>

	{#if data.planes.length}
		<div class="lista">
			<div class="resumen">
				<span class="t">Próximas 3 horas</span>
				<span class="s"
					>{data.planes.length} {data.planes.length === 1 ? 'plan cerca' : 'planes cerca'}</span
				>
			</div>
			{#each data.planes as p (p.id)}
				<PlanTarjeta plan={p} />
			{/each}
		</div>
	{:else}
		<div class="vacio">
			<Vacio
				titulo="No hay planes cerca. ¿Armás uno?"
				texto="Si publicás algo ahora, sos lo primero que ven."
			>
				<a class="gel gel-lima" href="/app/crear">Armá un plan</a>
				<a class="link" href="/app/explorar">Ver lo que hay mañana</a>
			</Vacio>
		</div>
	{/if}
</div>

<style>
	.arriba {
		padding: calc(20px + env(safe-area-inset-top)) 20px 0;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.donde {
		align-self: flex-start;
		display: flex;
		align-items: center;
		gap: 4px;
		font-size: 14px;
		font-weight: 600;
		color: #5c586b;
	}
	.titulo-fila {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	h1 {
		font:
			800 40px/1 'Geist',
			sans-serif;
		letter-spacing: -1.4px;
	}
	.seg-app {
		display: flex;
		padding: 4px;
		border-radius: 14px;
		background: #f0ecff;
	}
	.seg-app a {
		padding: 8px 14px;
		border-radius: 11px;
		font-size: 14px;
		font-weight: 600;
		color: #5c586b;
		text-decoration: none;
	}
	.seg-app a[aria-current='page'] {
		background: #ffffff;
		color: #24212d;
		font-weight: 700;
		box-shadow:
			inset 0 1px 0 #ffffff,
			0 3px 8px -2px rgba(80, 60, 160, 0.25);
	}
	.filtros {
		display: flex;
		gap: 8px;
		overflow-x: auto;
		margin: 0 -20px;
		padding: 0 20px 6px;
		scrollbar-width: none;
	}
	.filtros::-webkit-scrollbar {
		display: none;
	}
	.filtro {
		flex: none;
		padding: 9px 14px;
		border-radius: 14px;
		background: linear-gradient(180deg, #f8f6ff 0%, #ede8ff 100%);
		box-shadow:
			inset 0 1.5px 0 #ffffff,
			inset 0 -2px 0 rgba(116, 86, 241, 0.1);
		font-size: 14px;
		font-weight: 600;
		color: #24212d;
		text-decoration: none;
	}
	.filtro.on {
		background: url(/grain.png), radial-gradient(120% 90% at 0% 0%, #3d3756 0%, #24212d 62%);
		box-shadow:
			inset 0 1px 0 rgba(255, 255, 255, 0.08),
			0 12px 26px -10px rgba(36, 33, 45, 0.55);
		color: #faf9f6;
		font-weight: 700;
	}
	.lista {
		padding: 12px 20px 120px;
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	.resumen {
		display: flex;
		justify-content: space-between;
		align-items: baseline;
	}
	.resumen .t {
		font-size: 15px;
		font-weight: 700;
	}
	.resumen .s {
		font-size: 13px;
		color: #5c586b;
	}
	.vacio {
		padding: 56px 28px 120px;
	}
</style>
